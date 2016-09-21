package hu.bme.mit.theta.core.expr;

import java.util.List;

import hu.bme.mit.theta.core.type.ProcType;
import hu.bme.mit.theta.core.type.Type;

public interface ProcCallExpr<ReturnType extends Type> extends Expr<ReturnType> {

	public Expr<? extends ProcType<? extends ReturnType>> getProc();

	public List<? extends Expr<? extends Type>> getParams();

}