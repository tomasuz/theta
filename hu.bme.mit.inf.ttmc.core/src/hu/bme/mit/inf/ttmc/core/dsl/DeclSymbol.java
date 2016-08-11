package hu.bme.mit.inf.ttmc.core.dsl;

import static com.google.common.base.Preconditions.checkNotNull;

import hu.bme.mit.inf.ttmc.common.dsl.Symbol;
import hu.bme.mit.inf.ttmc.core.decl.Decl;

public class DeclSymbol implements Symbol {

	private static final int HASH_SEED = 8513;

	private volatile int hashCode = 0;

	private final Decl<?, ?> decl;

	public DeclSymbol(final Decl<?, ?> decl) {
		this.decl = checkNotNull(decl);
	}

	public Decl<?, ?> getDecl() {
		return decl;
	}

	@Override
	public String getName() {
		return decl.getName();
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = HASH_SEED;
			result = 31 * result + decl.hashCode();
			hashCode = result;
		}
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof DeclSymbol) {
			final DeclSymbol that = (DeclSymbol) obj;
			return this.decl.equals(that.decl);
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("DeclSymbol(");
		sb.append(decl.toString());
		sb.append(")");
		return sb.toString();
	}

}