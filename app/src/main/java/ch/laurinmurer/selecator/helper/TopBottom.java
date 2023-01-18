package ch.laurinmurer.selecator.helper;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Function;

public class TopBottom<T> {
	private final T top;
	private final T bottom;

	public TopBottom(T top, T bottom) {
		this.top = Objects.requireNonNull(top);
		this.bottom = Objects.requireNonNull(bottom);
	}

	public T top() {
		return top;
	}

	public T bottom() {
		return bottom;
	}

	public <R> TopBottom<R> map(Function<T, R> mappingFunction) {
		return new TopBottom<>(mappingFunction.apply(top), mappingFunction.apply(bottom));
	}

	@NonNull
	@Override
	public String toString() {
		return "TopBottom{" + top + " / " + bottom + '}';
	}
}
