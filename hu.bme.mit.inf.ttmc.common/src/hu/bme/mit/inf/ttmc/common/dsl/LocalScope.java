package hu.bme.mit.inf.ttmc.common.dsl;

import static com.google.common.base.Preconditions.checkNotNull;

class LocalScope extends BasicScope {

	LocalScope(final Scope enclosingScope) {
		super(checkNotNull(enclosingScope));
	}

}