package hu.bme.mit.inf.theta.formalism.common.stmt.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import hu.bme.mit.inf.theta.core.expr.Expr;
import hu.bme.mit.inf.theta.core.type.Type;
import hu.bme.mit.inf.theta.formalism.common.decl.VarDecl;
import hu.bme.mit.inf.theta.formalism.common.stmt.AssignStmt;

final class AssignStmtImpl<DeclType extends Type, ExprType extends DeclType> extends AbstractStmt
		implements AssignStmt<DeclType, ExprType> {

	private static final int HASH_SEED = 409;
	private volatile int hashCode = 0;

	private final VarDecl<DeclType> varDecl;
	private final Expr<ExprType> expr;

	AssignStmtImpl(final VarDecl<DeclType> varDecl, final Expr<ExprType> expr) {
		this.varDecl = checkNotNull(varDecl);
		this.expr = checkNotNull(expr);
	}

	@Override
	public VarDecl<DeclType> getVarDecl() {
		return varDecl;
	}

	@Override
	public Expr<ExprType> getExpr() {
		return expr;
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = HASH_SEED;
			result = 31 * result + varDecl.hashCode();
			result = 31 * result + expr.hashCode();
			hashCode = result;
		}
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof AssignStmt<?, ?>) {
			final AssignStmt<?, ?> that = (AssignStmt<?, ?>) obj;
			return this.getVarDecl().equals(that.getVarDecl()) && this.getExpr().equals(that.getExpr());
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Assign");
		sb.append("(");
		sb.append(varDecl.getName());
		sb.append(", ");
		sb.append(expr);
		sb.append(")");
		return sb.toString();
	}
}