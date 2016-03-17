package hu.bme.mit.inf.ttmc.constraint.expr.defaults;

import hu.bme.mit.inf.ttmc.constraint.ConstraintManager;
import hu.bme.mit.inf.ttmc.constraint.decl.ParamDecl;
import hu.bme.mit.inf.ttmc.constraint.expr.ParamRefExpr;
import hu.bme.mit.inf.ttmc.constraint.type.Type;
import hu.bme.mit.inf.ttmc.constraint.utils.ExprVisitor;

public abstract class AbstractParamRefExpr<DeclType extends Type> extends AbstractRefExpr<DeclType, ParamDecl<DeclType>>
		implements ParamRefExpr<DeclType> {

	private static final int HASH_SEED = 919;

	@SuppressWarnings("unused")
	private final ConstraintManager manager;

	public AbstractParamRefExpr(final ConstraintManager manager, final ParamDecl<DeclType> paramDecl) {
		super(paramDecl);
		this.manager = manager;
	}

	@Override
	public final DeclType getType() {
		return getDecl().getType();
	}

	@Override
	public final <P, R> R accept(final ExprVisitor<? super P, ? extends R> visitor, final P param) {
		return visitor.visit(this, param);
	}

	@Override
	public final boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof ParamRefExpr<?>) {
			final ParamRefExpr<?> that = (ParamRefExpr<?>) obj;
			return this.getDecl().equals(that.getDecl());
		} else {
			return false;
		}
	}

	@Override
	protected final int getHashSeed() {
		return HASH_SEED;
	}
}
