/*
 *  Copyright 2017 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.core.type.rattype;

import static com.google.common.base.Preconditions.checkArgument;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Bool;
import static hu.bme.mit.theta.core.type.rattype.RatExprs.Rat;

import com.google.common.math.IntMath;

import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.LitExpr;
import hu.bme.mit.theta.core.type.NullaryExpr;
import hu.bme.mit.theta.core.type.booltype.BoolLitExpr;

public final class RatLitExpr extends NullaryExpr<RatType> implements LitExpr<RatType>, Comparable<RatLitExpr> {

	private static final int HASH_SEED = 149;

	private final int num;
	private final int denom;

	private volatile int hashCode = 0;

	private RatLitExpr(final int num, final int denom) {
		checkArgument(denom != 0);

		final int gcd = IntMath.gcd(Math.abs(num), Math.abs(denom));
		if (denom >= 0) {
			this.num = num / gcd;
			this.denom = denom / gcd;
		} else {
			this.num = -num / gcd;
			this.denom = -denom / gcd;
		}
	}

	public static RatLitExpr of(final int num, final int denom) {
		return new RatLitExpr(num, denom);
	}

	@Override
	public RatType getType() {
		return Rat();
	}

	@Override
	public LitExpr<RatType> eval(final Valuation val) {
		return this;
	}

	public int getNum() {
		return num;
	}

	public int getDenom() {
		return denom;
	}

	public int sign() {
		return (num < 0) ? -1 : (num == 0) ? 0 : 1;
	}

	public int floor() {
		if (num >= 0 || num % denom == 0) {
			return num / denom;
		} else {
			return num / denom - 1;
		}
	}

	public int ceil() {
		if (num <= 0 || num % denom == 0) {
			return num / denom;
		} else {
			return num / denom + 1;
		}
	}

	public RatLitExpr add(final RatLitExpr that) {
		return RatLitExpr.of(this.getNum() * that.getDenom() + this.getDenom() * that.getNum(),
				this.getDenom() * that.getDenom());
	}

	public RatLitExpr sub(final RatLitExpr that) {
		return RatLitExpr.of(this.getNum() * that.getDenom() - this.getDenom() * that.getNum(),
				this.getDenom() * that.getDenom());
	}

	public RatLitExpr neg() {
		return RatLitExpr.of(-this.getNum(), this.getDenom());
	}

	public RatLitExpr mul(final RatLitExpr that) {
		return RatLitExpr.of(this.getNum() * that.getNum(), this.getDenom() * that.getDenom());
	}

	public RatLitExpr div(final RatLitExpr that) {
		return RatLitExpr.of(this.getNum() * that.getDenom(), this.getDenom() * that.getNum());
	}

	public BoolLitExpr eq(final RatLitExpr that) {
		return Bool(this.getNum() == that.getNum() && this.getDenom() == that.getDenom());
	}

	public BoolLitExpr neq(final RatLitExpr that) {
		return Bool(this.getNum() != that.getNum() || this.getDenom() != that.getDenom());
	}

	public BoolLitExpr lt(final RatLitExpr that) {
		return Bool(this.getNum() * that.getDenom() < this.getDenom() * that.getNum());
	}

	public BoolLitExpr leq(final RatLitExpr that) {
		return Bool(this.getNum() * that.getDenom() <= this.getDenom() * that.getNum());
	}

	public BoolLitExpr gt(final RatLitExpr that) {
		return Bool(this.getNum() * that.getDenom() > this.getDenom() * that.getNum());
	}

	public BoolLitExpr geq(final RatLitExpr that) {
		return Bool(this.getNum() * that.getDenom() >= this.getDenom() * that.getNum());
	}

	public RatLitExpr abs() {
		return RatLitExpr.of(Math.abs(num), denom);
	}

	public RatLitExpr frac() {
		return sub(of(floor(), 1));
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = HASH_SEED;
			result = 31 * result + num;
			result = 31 * result + denom;
			hashCode = result;
		}
		return hashCode;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof RatLitExpr) {
			final RatLitExpr that = (RatLitExpr) obj;
			return (this.getNum() == that.getNum() && this.getDenom() == that.getDenom());
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(getNum());
		sb.append('%');
		sb.append(getDenom());
		return sb.toString();
	}

	@Override
	public int compareTo(final RatLitExpr that) {
		return Integer.compare(this.getNum() * that.getDenom(), this.getDenom() * that.getNum());
	}

}
