package hu.bme.mit.theta.xcfa.simulator;

import hu.bme.mit.theta.core.decl.IndexedConstDecl;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.MutableValuation;
import hu.bme.mit.theta.core.stmt.AssignStmt;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.LitExpr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.core.utils.VarIndexing;
import hu.bme.mit.theta.xcfa.XCFA;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Actual state of execution
 *
 * Simulates the call-stack (with where-to-return locations) for every process.
 * Stores variable values in a common Valuation.
 * Handles recursion: with VarIndexing chooses the current version of the variable is always known..
 *
 * Currently uninitialised variables only work for integers (and it assigns zero).
 *
 * (currently only) TracedExplState extends this class, thus it must be able to be copied.
 *
 * Simulating multiple execution orders is copy-and-step: copy, to have the older data,
 * 	  and then step with both RuntimeStates with different transitions.
 *
 * Every derived class should override copy() and implementing protected DerivedExplState(DerivedExplState)
 * to be able to copy the exact state with the type.
 * Used to be able to trace execution in TracedExplState and to use copy-and-step.
 */
public class ExplState {
	private Map<XCFA.Process, ProcessState> processStates;
	private XCFA xcfa;

	/** Cached answer for getSafety(). Initialized on first call. */
	private StateSafety safety = null;

	/** Cached answer for getEnabledTransition(). Initialized on first call. */
	private Collection<Transition> enabledTransitions = null;

	/**
	 * Stores all values for all versions of variables (for every depth in the stack)
	 */
	private MutableValuation valuation;
	/**
	 * Stores the current depth of every variable.
	 * TODO use only for procedure-local vars
	 */
	private VarIndexing vars;

	ProcessState getProcessState(XCFA.Process process) {
		return processStates.get(process);
	}

	/**
	 * Creates an initial state from the given XCFA
	 */
	public ExplState(XCFA xcfa) {
		valuation = new MutableValuation();
		vars = VarIndexing.builder(0).build();
		this.xcfa = xcfa;
		List<XCFA.Process> procs = xcfa.getProcesses();
		processStates = new HashMap<>();
		for (XCFA.Process proc : procs) {
			processStates.put(proc, new ProcessState(this, proc));
		}
	}

	/**
	 * Used to be clone a state with an inherited type.
	 */
	protected ExplState(ExplState toCopy) {
		valuation = MutableValuation.copyOf(toCopy.valuation);
		vars = toCopy.vars.transform().build();
		xcfa = toCopy.xcfa; // no need to copy static structure
		processStates = new HashMap<>();
		for (Map.Entry<XCFA.Process, ProcessState> entry : toCopy.processStates.entrySet()) {
			processStates.put(entry.getKey(), new ProcessState(this, entry.getValue()));
		}
	}

	/**
	 * Copies the whole dynamic explicit state.
	 * Reuses static structures like the XCFA graph.
	 * Should be overridden for every derived class.
	 * See TracedExplState for exact way to derivation.
	 */
	protected ExplState copy() {
		return new ExplState(this);
	}

	/**
	 * Returns the list of enabled transitions.
	 */
	public Collection<Transition> getEnabledTransitions() {
		if (enabledTransitions != null)
			return enabledTransitions;
		ArrayList<Transition> result = new ArrayList<>();
		for (Map.Entry<XCFA.Process, ProcessState> entry : processStates.entrySet()) {
			entry.getValue().collectEnabledTransitions(result);
		}
		return enabledTransitions = result;
	}

	public static class StateSafety {
		public final boolean safe;
		public final boolean finished;
		/** Human readable message in case of unsafety. null if safe */
		public final String message;

		private StateSafety(boolean safe, boolean finished, String message) {
			this.safe = safe;
			this.finished = finished;
			this.message = message;
		}
	}

	public StateSafety getSafety() {
		if (safety != null)
			return safety;
		if (isFinished()) {
			return safety = new StateSafety(true, true, null);
		}
		if (!isSafe()) {
			return safety = new StateSafety(false, false, "Error location reached.");
		}
		if (getEnabledTransitions().isEmpty()) {
			return safety = new StateSafety(false, false, "Deadlock reached.");
		}
		return safety = new StateSafety(true, false, null);
	}

	/**
	 * Merges getEnabledTransitions + doTransition with a Scheduler which chooses a transition from the list given.
	 * Difference is, doTransition creates a new copy a transition ahead, without `this` changed.
	 * @param sched A Scheduler which chooses between enabled transitions
	 */
	public void step(Scheduler sched) {
		// TODO edge from final location might lead to infinite loop or "deadlock"
		onChange();
		Collection<Transition> enabledTransitions = getEnabledTransitions();
		sched.getNextTransition(enabledTransitions).step(this);
	}

	/** Returns true when every thread has finished successfully,
	 * meaning that every thread has exit its main procedure. */
	private boolean isFinished() {
		for (Map.Entry<XCFA.Process, ProcessState> entry : processStates.entrySet()) {
			if (!entry.getValue().isFinished())
				return false;
		}
		return true;
	}

	public boolean isSafe() {
		for (Map.Entry<XCFA.Process, ProcessState> entry : processStates.entrySet()) {
			if (!entry.getValue().isSafe())
				return false;
		}
		return true;
	}

	/**
	 * Returns the variable to be used in the current scope.
	 *
	 * Recursive functions might have multiple versions of the same local variable.
	 * This returns the active call's version of the variable.
	 * Used by CallState and/or ProcessState.
	 * @param var Variable
	 * @param <DeclType> The type of the variable given
	 * @return Indexed variable
	 */
	private <DeclType extends Type> IndexedConstDecl<DeclType> getCurrentVar(VarDecl<DeclType> var) {
		return var.getConstDecl(vars.get(var));
	}

	/**
	 * Updates a variable with the given expression of the same type.
	 * Used by CallState and/or ProcessState.
	 */
	<DeclType extends Type> void updateVariable(VarDecl<DeclType> variable, LitExpr<DeclType> litExpr) {
		valuation.put(getCurrentVar(variable), litExpr);
	}

	/**
	 * Returns current value of the given variable.
	 * @return Returns the value or Optional.empty() when variable has no value
	 */
	<DeclType extends Type> Optional<LitExpr<DeclType>> evalVariable(VarDecl<DeclType> variable) {
		return valuation.eval(getCurrentVar(variable));
	}

	/** Interface used by CallState & ProcessState to update variable storage. */
	void havocVariable(VarDecl<? extends Type> variable) {
		valuation.remove(getCurrentVar(variable));
	}

	/** Interface used by CallState & ProcessState to update variable storage. */
	void pushVariable(VarDecl<? extends Type> variable) {
		vars = vars.inc(variable);
	}

	/** Interface used by CallState & ProcessState to update variable storage. */
	void popVariable(VarDecl<? extends Type> variable) {
		vars = vars.inc(variable, -1);
	}

	/** Interface used by CallState & ProcessState to update variable storage. */
	<DeclType extends Type> LitExpr<DeclType> evalExpr(Expr<DeclType> expr) {
		Expr<DeclType> unfolded = PathUtils.unfold(expr, vars);
		FillValuation.getInstance().fill(unfolded, valuation);
		return unfolded.eval(valuation);
	}

	/** Interface used by CallState & ProcessState to update variable storage. */
	<DeclType extends Type> void assign(AssignStmt<DeclType> stmt) {
		LitExpr<DeclType> x = evalExpr(stmt.getExpr());
		updateVariable(stmt.getVarDecl(), x);
	}

	private void onChange() {
		safety = null;
		enabledTransitions = null;
	}

	/**
	 * Returns a new state one transition ahead, without changing `this`'s data
	 * TODO rename doTransition to executeTransition
	 * @param transition Transition to execute in the new state
	 * @return A new state one transition ahead.
	 */
	public ExplState doTransition(Transition transition) {
		onChange();
		ExplState newState = copy();
		transition.step(newState);
		return newState;
	}
}
