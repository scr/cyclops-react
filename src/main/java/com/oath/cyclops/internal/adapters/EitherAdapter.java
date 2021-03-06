package com.oath.cyclops.internal.adapters;

import com.oath.cyclops.types.anyM.AnyMValue;
import com.oath.cyclops.types.extensability.AbstractFunctionalAdapter;
import com.oath.cyclops.types.extensability.FunctionalAdapter;
import com.oath.cyclops.types.extensability.ValueAdapter;
import cyclops.control.Option;
import cyclops.control.Either;
import cyclops.monads.AnyM;
import cyclops.monads.Witness;
import cyclops.monads.Witness.either;
import lombok.AllArgsConstructor;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

@AllArgsConstructor
public class EitherAdapter extends AbstractFunctionalAdapter<either> implements ValueAdapter<either> {






    @Override
    public boolean isFilterable(){
        return false;
    }



    public <T> Option<T> get(AnyMValue<either,T> t){
        return xor(t).toOption();
    }
    @Override
    public <T> Iterable<T> toIterable(AnyM<either, T> t) {
        return xor(t);
    }

    public <R> R visit(Function<? super FunctionalAdapter<either>,? extends R> fn1, Function<? super ValueAdapter<either>, ? extends R> fn2){
        return fn2.apply(this);
    }

    public <T> Either<?,T> xor(AnyM<either, T> t){
        return (Either<?,T>)t.unwrap();
    }
    @Override
    public <T> AnyM<either, T> filter(AnyM<either, T> t, Predicate<? super T> fn) {
        return t;
    }


    @Override
    public <T> AnyM<either, T> empty() {
        return AnyM.fromLazyEither(Either.left(null));

    }

    @Override
    public <T, R> AnyM<either, R> ap(AnyM<either,? extends Function<? super T, ? extends R>> fn, AnyM<either, T> apply) {
        return flatMap(apply,x->fn.map(fnA->fnA.apply(x)));

    }

    @Override
    public <T, R> AnyM<either, R> flatMap(AnyM<either, T> t,
                                          Function<? super T, ? extends AnyM<either, ? extends R>> fn) {

        return AnyM.fromLazyEither(Witness.either(t).flatMap(fn.andThen(Witness::either)));

    }

    @Override
    public <T, R> AnyM<either, R> map(AnyM<either, T> t, Function<? super T, ? extends R> fn) {
        return AnyM.fromLazyEither(Witness.either(t).map(fn));
    }

    @Override
    public <T> AnyM<either, T> unitIterable(Iterable<T> it) {
       return AnyM.fromLazyEither(fromIterable(it));
    }

    @Override
    public <T> AnyM<either, T> unit(T o) {
        return AnyM.fromLazyEither(Either.right(o));
    }

    public static <ST, T> Either<ST, T> fromIterable(final Iterable<T> iterable) {

        final Iterator<T> it = iterable.iterator();
        return it.hasNext() ? Either.right( it.next()) : Either.left(null);
    }

}
