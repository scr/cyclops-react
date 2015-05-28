package com.aol.cyclops.lambda.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.aol.cyclops.lambda.monads.ComprehenderSelector;

/**
 * Interface for defining how Comprehensions should work for a type
 * Cyclops For Comprehensions will supply either a JDK 8 Predicate or Function
 * for filter / map / flatMap
 * The comprehender should wrap these in a suitable type and make the call to the
 * underlying Monadic Type (T) the Comprehender implementation supports.
 * 
 * E.g. To support mapping for the Functional Java Option type wrap the supplied JDK 8 Function in a Functional Java
 * fj.F type, call the make call to option.map( ) and retun the result.
 * 
 * <pre>
 *  OptionComprehender&lt;Option&gt; {
 *    
 *     public Object map(Option o, Function fn){
 *        return o.map( a-&gt; fn.apply(a));
 *     }
 *     
 * }
 * </pre>
 * 
 * @author johnmcclean
 *
 * @param <T> Monadic Type being wrapped
 */
public interface Comprehender<T> {

	
	/**
	 * Wrapper around filter
	 * 
	 * @param t Monadic type being wrapped
	 * @param p JDK Predicate to wrap
	 * @return Result of call to t.filter ( i -> p.test(i));
	 */
	public Object filter(T t, Predicate p);
	
	/**
	 * Wrapper around map
	 * 
	 * @param t Monadic type being wrapped
	 * @param fn JDK Function to wrap
	 * @return Result of call to t.map( i -> fn.apply(i));
	 */
	public Object map(T t, Function fn);
	
	/**
	 * Wrapper around flatMap
	 * 
	 * @param t Monadic type being wrapped
	 * @param fn JDK Function to wrap
	 * @return Result of call to t.flatMap( i -> fn.apply(i));
	 */
	default T executeflatMap(T t, Function fn){
		return flatMap(t,input -> unwrapOtherMonadTypes(fn.apply(input)));
	}
	public T flatMap(T t, Function fn);
	
	public boolean instanceOfT(Object apply);
	public T of(Object o);
	public T of();
	
	default T unwrapOtherMonadTypes(Object apply){

		if (instanceOfT(apply))
			return (T) apply;

		if (apply instanceof Optional) {
			if (((Optional) apply).isPresent())
				return of(((Optional) apply).get());
			return of();
		}
		if (apply instanceof Stream) {
			return of(((Stream) apply).collect(Collectors.toList()));
		}
		if (apply instanceof IntStream) {
			return of(((IntStream) apply).boxed().collect(Collectors.toList()));
		}
		if (apply instanceof DoubleStream) {
			return of(((DoubleStream) apply).boxed().collect(Collectors.toList()));
		}
		if (apply instanceof LongStream) {
			return of(((DoubleStream) apply).boxed().collect(Collectors.toList()));
		}
		if (apply instanceof CompletableFuture) {
			return of(((CompletableFuture) apply).join());
		}

		return (T) new ComprehenderSelector().selectComprehender(apply)
				.handleReturnForFlatMap(apply);

	}
	
	default T handleReturnForFlatMap(Object apply){
		return (T)apply;//new InvokeDynamic().execute(Arrays.asList("get"),apply);
	}

	public Class getTargetClass();
	
}
