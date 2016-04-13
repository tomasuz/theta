package hu.bme.mit.inf.ttmc.cegar.clusteredcegar.steps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hu.bme.mit.inf.ttmc.cegar.clusteredcegar.data.ClusteredAbstractState;
import hu.bme.mit.inf.ttmc.cegar.clusteredcegar.data.ClusteredAbstractSystem;
import hu.bme.mit.inf.ttmc.cegar.clusteredcegar.data.ComponentAbstractState;
import hu.bme.mit.inf.ttmc.cegar.common.data.ConcreteTrace;
import hu.bme.mit.inf.ttmc.cegar.common.data.KripkeStructure;
import hu.bme.mit.inf.ttmc.cegar.common.steps.AbstractCEGARStep;
import hu.bme.mit.inf.ttmc.cegar.common.steps.Refiner;
import hu.bme.mit.inf.ttmc.cegar.common.utils.SolverHelper;
import hu.bme.mit.inf.ttmc.cegar.common.utils.visualization.Visualizer;
import hu.bme.mit.inf.ttmc.common.logging.Logger;
import hu.bme.mit.inf.ttmc.core.expr.AndExpr;
import hu.bme.mit.inf.ttmc.core.expr.Expr;
import hu.bme.mit.inf.ttmc.core.type.BoolType;
import hu.bme.mit.inf.ttmc.core.type.Type;
import hu.bme.mit.inf.ttmc.formalism.common.decl.VarDecl;
import hu.bme.mit.inf.ttmc.formalism.sts.STS;
import hu.bme.mit.inf.ttmc.formalism.sts.STSManager;
import hu.bme.mit.inf.ttmc.solver.Solver;

public class ClusteredRefiner extends AbstractCEGARStep
		implements Refiner<ClusteredAbstractSystem, ClusteredAbstractState> {

	public ClusteredRefiner(final Logger logger, final Visualizer visualizer) {
		super(logger, visualizer);
	}

	@Override
	public ClusteredAbstractSystem refine(final ClusteredAbstractSystem system,
			final List<ClusteredAbstractState> abstractCounterEx, final ConcreteTrace concreteTrace) {

		final Solver solver = system.getManager().getSolverFactory().createSolver(true, false);
		final int traceLength = concreteTrace.size();
		assert (1 <= traceLength && traceLength < abstractCounterEx.size());

		// The failure state is the last state in the abstract counterexample
		// which
		// can be reached with a concrete path
		final ClusteredAbstractState failureState = abstractCounterEx.get(traceLength - 1);
		logger.writeln("Failure state: " + failureState, 4, 0);

		final STS sts = system.getSTS();

		final List<AndExpr> deadEndStates = getDeadEndStates(abstractCounterEx, traceLength, solver,
				system.getManager(), sts);

		// Bad states are not needed currently, they can be collected for
		// debugging
		/// List<Model> badStates = getBadStates(abstractCounterEx, traceLength,
		// solver, system.getManager(), sts);

		int clusterNo = -1;
		// Loop through each component of the failure state
		int newStates = 0;
		for (final ComponentAbstractState as : failureState.getStates()) {
			if (isStopped)
				return null;
			++clusterNo;
			logger.write("Refining component: ", 5, 2);
			logger.writeln(as, 5, 0);
			// Get concrete states in the abstract state (projected)
			final List<AndExpr> concreteStates = getConcreteStatesOfAbstractState(as, solver, system.getManager(), sts);

			// Cannot refine if there is only one state
			if (concreteStates.size() == 1)
				continue;

			// Currently every concrete state is in the same equivalence class.
			// Refinement
			// means to partition this equivalence class into some smaller
			// classes

			// For each state, collect the compatible states
			final List<List<AndExpr>> compatibility = new ArrayList<>(concreteStates.size());

			// Get variables outside the cluster
			final Set<VarDecl<? extends Type>> otherVars = new HashSet<>(system.getVars());
			otherVars.removeAll(as.getCluster().getVars());

			// Loop through pairs of states and check if they should be
			// separated
			for (int i = 0; i < concreteStates.size(); ++i) {
				if (isStopped)
					return null;
				final List<AndExpr> comp = new ArrayList<>();
				comp.add(concreteStates.get(i)); // The state is compatible with
													// itself
				for (int j = i + 1; j < concreteStates.size(); ++j) // Check
																	// other
																	// states
					if (checkPair(as, concreteStates.get(i), concreteStates.get(j), deadEndStates, otherVars, solver,
							sts))

						comp.add(concreteStates.get(j));
				compatibility.add(comp);
			}

			// If the first state is compatible with every other, this means
			// that no
			// refinement is needed (every state stays in the same equivalence
			// class
			if (compatibility.get(0).size() == concreteStates.size())
				continue;

			// Collect the new equivalence classes with their expressions
			final List<Expr<? extends BoolType>> eqclasses = new ArrayList<>();
			final Set<AndExpr> includedStates = new HashSet<>();
			// Loop through each state and if it was not included in an
			// equivalence class yet,
			// then include it with its equivalence class
			for (int i = 0; i < compatibility.size(); ++i) {
				if (isStopped)
					return null;
				if (!includedStates.contains(concreteStates.get(i))) {
					if (compatibility.get(i).size() == 1) // If it is a single
															// state ->
															// expression of the
															// state
						eqclasses.add(compatibility.get(i).get(0));
					else // If there are more states -> or expression of the
							// expressions of the states
						eqclasses.add(system.getManager().getExprFactory().Or(compatibility.get(i)));

					for (final AndExpr cs : compatibility.get(i))
						includedStates.add(cs);
				}
			}

			assert (eqclasses.size() > 1);
			logger.writeln(eqclasses.size() + " new abstract states.", 5, 3);
			for (final Expr<? extends BoolType> ex : eqclasses)
				logger.writeln(ex, 7, 4);

			// Refine the abstract Kripke structure
			final KripkeStructure<ComponentAbstractState> ks = system.getAbstractKripkeStructure(clusterNo);

			// Remove the original state
			ks.getStates().remove(as);
			ks.getInitialStates().remove(as);

			// Create refined abstract states from the equivalence classes
			final List<ComponentAbstractState> refinedStates = new ArrayList<>(eqclasses.size());
			int eqCounter = 0;
			for (final Expr<? extends BoolType> eqclass : eqclasses)
				refinedStates.add(as.refine(eqCounter++, eqclass));

			// Check if the refined states are initial (only if the original
			// state was initial, but
			// then at least one of the refined states must also be initial -->
			// assertion)
			if (as.isInitial()) {
				solver.push();
				solver.add(sts.unrollInv(0));
				solver.add(sts.unrollInit(0));
				boolean isInitial = false;
				for (final ComponentAbstractState refined : refinedStates) {
					solver.push();
					SolverHelper.unrollAndAssert(solver, refined.getLabels(), sts, 0);
					if (SolverHelper.checkSat(solver)) {
						refined.setInitial(true);
						isInitial = true;
					}
					solver.pop();
				}
				assert (isInitial);
				solver.pop();
			}

			// Get successors for the abstract states (only the successors of
			// the original state
			// have to be checked, but every successor must belong to at least
			// one of the
			// refined states --> assertion)
			solver.push();
			solver.add(sts.unrollInv(0));
			solver.add(sts.unrollInv(1));
			solver.add(sts.unrollTrans(0));
			for (final ComponentAbstractState succ : as.getSuccessors()) {
				if (isStopped)
					return null;
				if (succ.equals(as))
					continue;
				solver.push();
				SolverHelper.unrollAndAssert(solver, succ.getLabels(), sts, 1);
				boolean isSuccessor = false;
				for (final ComponentAbstractState refined : refinedStates) {
					solver.push();
					SolverHelper.unrollAndAssert(solver, refined.getLabels(), sts, 0);
					if (SolverHelper.checkSat(solver)) {
						refined.getSuccessors().add(succ);
						isSuccessor = true;
					}
					solver.pop();
				}
				assert (isSuccessor);
				solver.pop();
			}

			// Check transitions between refined states
			for (final ComponentAbstractState ref0 : refinedStates) {
				if (isStopped)
					return null;
				solver.push();
				SolverHelper.unrollAndAssert(solver, ref0.getLabels(), sts, 0);
				for (final ComponentAbstractState ref1 : refinedStates) {
					solver.push();
					SolverHelper.unrollAndAssert(solver, ref1.getLabels(), sts, 1);
					if (SolverHelper.checkSat(solver))
						ref0.getSuccessors().add(ref1);
					solver.pop();
				}

				solver.pop();
			}

			// TODO: store predecessor states
			// Update other states' successors: if the original state was a
			// successor, then remove it
			// and check which refined states are successors (at least one must
			// be --> assertion)
			for (final ComponentAbstractState prev : ks.getStates()) {
				if (isStopped)
					return null;
				if (prev.getSuccessors().contains(as)) {
					boolean isSuccessor = false;
					prev.getSuccessors().remove(as);
					solver.push();
					SolverHelper.unrollAndAssert(solver, prev.getLabels(), sts, 0);
					for (final ComponentAbstractState refined : refinedStates) {
						solver.push();
						SolverHelper.unrollAndAssert(solver, refined.getLabels(), sts, 1);
						if (SolverHelper.checkSat(solver)) {
							prev.getSuccessors().add(refined);
							isSuccessor = true;
						}
						solver.pop();
					}
					assert (isSuccessor);
					solver.pop();
				}
			}
			solver.pop();

			// Add new states
			ks.getStates().addAll(refinedStates);
			for (final ComponentAbstractState refined : refinedStates)
				if (refined.isInitial())
					ks.addInitialState(refined);
			newStates += refinedStates.size();
		}

		assert (newStates > 0);

		return system;
	}

	private List<AndExpr> getDeadEndStates(final List<ClusteredAbstractState> abstractCounterEx, final int traceLength,
			final Solver solver, final STSManager manager, final STS sts) {

		final List<AndExpr> deadEndStates = new ArrayList<>();
		solver.push();
		solver.add(sts.unrollInit(0)); // Assert initial conditions
		for (int i = 0; i < traceLength; ++i) {
			solver.add(sts.unrollInv(i)); // Invariants
			for (final ComponentAbstractState as : abstractCounterEx.get(i).getStates())
				for (final Expr<? extends BoolType> label : as.getLabels())
					solver.add(sts.unroll(label, i)); // Labels
			if (i > 0)
				solver.add(sts.unrollTrans(i - 1)); // Transition relation
		}

		do {
			if (SolverHelper.checkSat(solver)) {
				// Get dead-end state
				final AndExpr ds = sts.getConcreteState(solver.getModel(), traceLength - 1);
				logger.write("Dead end state: ", 6, 1);
				logger.writeln(ds, 6, 0);
				deadEndStates.add(ds);

				// Exclude this state in order to get new dead end states
				solver.add(sts.unroll(manager.getExprFactory().Not(ds), traceLength - 1));
			} else
				break;
		} while (true);

		solver.pop();
		return deadEndStates;
	}

	@SuppressWarnings("unused")
	private List<AndExpr> getBadStates(final List<ClusteredAbstractState> abstractCounterEx, final int traceLength,
			final Solver solver, final STSManager manager, final STS sts) {

		final List<AndExpr> badStates = new ArrayList<>();
		solver.push();
		solver.add(sts.unrollInv(0)); // Invariants
		solver.add(sts.unrollInv(1)); // Invariants
		// Failure state
		for (final ComponentAbstractState as : abstractCounterEx.get(traceLength - 1).getStates())
			for (final Expr<? extends BoolType> label : as.getLabels())
				solver.add(sts.unroll(label, 0)); // Labels
		// Next state
		for (final ComponentAbstractState as : abstractCounterEx.get(traceLength).getStates())
			for (final Expr<? extends BoolType> label : as.getLabels())
				solver.add(sts.unroll(label, 1)); // Labels
		solver.add(sts.unrollTrans(0)); // Transition relation

		do {
			if (SolverHelper.checkSat(solver)) {
				// Get bad state
				final AndExpr bs = sts.getConcreteState(solver.getModel(), 0);
				logger.write("Bad state: ", 6, 1);
				logger.writeln(bs, 6, 0);
				badStates.add(bs);

				// Exclude this state in order to get new dead end states
				solver.add(sts.unroll(manager.getExprFactory().Not(bs), 0));
			} else
				break;
		} while (true);
		solver.pop();
		return badStates;
	}

	private List<AndExpr> getConcreteStatesOfAbstractState(final ComponentAbstractState abstractState,
			final Solver solver, final STSManager manager, final STS sts) {

		final List<AndExpr> concreteStates = new ArrayList<>();
		solver.push();
		solver.add(sts.unrollInv(0));
		// Assert the labels of the state
		for (final Expr<? extends BoolType> label : abstractState.getLabels())
			solver.add(sts.unroll(label, 0));
		do {
			if (SolverHelper.checkSat(solver)) {
				// Get the model and project
				final AndExpr cs = sts.getConcreteState(solver.getModel(), 0, abstractState.getCluster().getVars());

				concreteStates.add(cs);
				// Exclude this state to get new ones
				solver.add(sts.unroll(manager.getExprFactory().Not(cs), 0));
				logger.write("Concrete state: ", 7, 3);
				logger.writeln(cs, 7, 0);
			} else
				break;
		} while (true);
		solver.pop();
		return concreteStates;
	}

	private boolean checkPair(final ComponentAbstractState as, final AndExpr cs0, final AndExpr cs1,
			final List<AndExpr> deadEndStates, final Set<VarDecl<? extends Type>> otherVars, final Solver solver,
			final STS sts) {

		// Project the dead-end states and check if they are equal
		final List<AndExpr> proj0 = projectDeadEndStates(as, cs0, deadEndStates, otherVars, solver, sts);
		final List<AndExpr> proj1 = projectDeadEndStates(as, cs1, deadEndStates, otherVars, solver, sts);
		if (proj0.size() != proj1.size())
			return false;

		for (final AndExpr p0 : proj0) {
			boolean found = false;
			for (final AndExpr p1 : proj1) {
				if (p0.equals(p1)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}

		return true;
	}

	private List<AndExpr> projectDeadEndStates(final ComponentAbstractState as, final AndExpr cs,
			final List<AndExpr> deadEndStates, final Set<VarDecl<? extends Type>> otherVars, final Solver solver,
			final STS sts) {

		// Example: concrete state: (x=1,y=2)
		// The dead-end states are: (x=1,y=2,z=3), (x=1,y=3,z=2), (x=1,y=2,z=5)
		// The result is: (z=3), (z=5)

		final List<AndExpr> ret = new ArrayList<>();
		solver.push();
		solver.add(sts.unroll(cs, 0));
		for (final AndExpr ds : deadEndStates) {
			solver.push();
			solver.add(sts.unroll(ds, 0));
			if (SolverHelper.checkSat(solver))
				ret.add(sts.getConcreteState(solver.getModel(), 0, otherVars));
			solver.pop();
		}
		solver.pop();
		return ret;
	}

	@Override
	public String toString() {
		return "";
	}
}