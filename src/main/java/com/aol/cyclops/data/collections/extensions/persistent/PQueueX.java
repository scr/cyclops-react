package com.aol.cyclops.data.collections.extensions.persistent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.pcollections.AmortizedPQueue;
import org.pcollections.PQueue;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.Reducers;
import com.aol.cyclops.control.Matchable.CheckValue1;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.Trampoline;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.Combiner;
import com.aol.cyclops.types.OnEmptySwitch;
import com.aol.cyclops.types.To;
import com.aol.cyclops.types.Value;

public interface PQueueX<T> extends To<PQueueX<T>>,PQueue<T>, PersistentCollectionX<T>, OnEmptySwitch<T, PQueue<T>> {

    /**
     * Narrow a covariant PQueueX
     * 
     * <pre>
     * {@code 
     *  PQueueX<? extends Fruit> set = PQueueX.of(apple,bannana);
     *  PQueueX<Fruit> fruitSet = PQueueX.narrow(set);
     * }
     * </pre>
     * 
     * @param queueX to narrow generic type
     * @return POrderedSetX with narrowed type
     */
    public static <T> PQueueX<T> narrow(final PQueueX<? extends T> queueX) {
        return (PQueueX<T>) queueX;
    }
    
    /**
     * Create a PQueueX that contains the Integers between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range PQueueX
     */
    public static PQueueX<Integer> range(final int start, final int end) {
        return ReactiveSeq.range(start, end)
                          .toPQueueX();
    }

    /**
     * Create a PQueueX that contains the Longs between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range PQueueX
     */
    public static PQueueX<Long> rangeLong(final long start, final long end) {
        return ReactiveSeq.rangeLong(start, end)
                          .toPQueueX();
    }

    /**
     * Unfold a function into a PQueueX
     * 
     * <pre>
     * {@code 
     *  PQueueX.unfold(1,i->i<=6 ? Optional.of(Tuple.tuple(i,i+1)) : Optional.empty());
     * 
     * //(1,2,3,4,5)
     * 
     * }</code>
     * 
     * @param seed Initial value 
     * @param unfolder Iteratively applied function, terminated by an empty Optional
     * @return PQueueX generated by unfolder function
     */
    static <U, T> PQueueX<T> unfold(final U seed, final Function<? super U, Optional<Tuple2<T, U>>> unfolder) {
        return ReactiveSeq.unfold(seed, unfolder)
                          .toPQueueX();
    }

    /**
     * Generate a PQueueX from the provided Supplier up to the provided limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param s Supplier to generate PQueueX elements
     * @return PQueueX generated from the provided Supplier
     */
    public static <T> PQueueX<T> generate(final long limit, final Supplier<T> s) {

        return ReactiveSeq.generate(s)
                          .limit(limit)
                          .toPQueueX();
    }

    /**
     * Create a PQueueX by iterative application of a function to an initial element up to the supplied limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param seed Initial element
     * @param f Iteratively applied to each element to generate the next element
     * @return PQueueX generated by iterative application
     */
    public static <T> PQueueX<T> iterate(final long limit, final T seed, final UnaryOperator<T> f) {
        return ReactiveSeq.iterate(seed, f)
                          .limit(limit)
                          .toPQueueX();

    }

    public static <T> PQueueX<T> of(final T... values) {
        PQueue<T> result = empty();
        for (final T value : values) {
            result = result.plus(value);
        }

        return new PQueueXImpl<>(
                                 result);
    }

    public static <T> PQueueX<T> empty() {
        return new PQueueXImpl<>(
                                 AmortizedPQueue.empty());
    }

    public static <T> PQueueX<T> singleton(final T value) {
        return PQueueX.<T> empty()
                      .plus(value);
    }

    /**
     * Construct a PQueueX from an Publisher
     * 
     * @param publisher
     *            to construct PQueueX from
     * @return PQueueX
     */
    public static <T> PQueueX<T> fromPublisher(final Publisher<? extends T> publisher) {
        return ReactiveSeq.fromPublisher((Publisher<T>) publisher)
                          .toPQueueX();
    }

    public static <T> PQueueX<T> fromIterable(final Iterable<T> iterable) {
        if (iterable instanceof PQueueX)
            return (PQueueX) iterable;
        if (iterable instanceof PQueue)
            return new PQueueXImpl<>(
                                     (PQueue) iterable);
        PQueue<T> res = empty();
        final Iterator<T> it = iterable.iterator();
        while (it.hasNext())
            res = res.plus(it.next());

        return new PQueueXImpl<>(
                                 res);
    }

    public static <T> PQueueX<T> fromCollection(final Collection<T> stream) {
        if (stream instanceof PQueueX)
            return (PQueueX) stream;
        if (stream instanceof PQueue)
            return new PQueueXImpl<>(
                                     (PQueue) stream);
        return PQueueX.<T> empty()
                      .plusAll(stream);
    }

    public static <T> PQueueX<T> fromStream(final Stream<T> stream) {
        return Reducers.<T> toPQueueX()
                       .mapReduce(stream);
    }

    /**
     * Combine two adjacent elements in a PQueueX using the supplied
     * BinaryOperator This is a stateful grouping & reduction operation. The
     * output of a combination may in turn be combined with it's neighbor
     * 
     * <pre>
     * {@code 
     *  PQueueX.of(1,1,2,3)
                   .combine((a, b)->a.equals(b),Semigroups.intSum)
                   .toListX()
                   
     *  //ListX(3,4) 
     * }
     * </pre>
     * 
     * @param predicate
     *            Test to see if two neighbors should be joined
     * @param op
     *            Reducer to combine neighbors
     * @return Combined / Partially Reduced PQueueX
     */
    @Override
    default PQueueX<T> combine(final BiPredicate<? super T, ? super T> predicate, final BinaryOperator<T> op) {
        return (PQueueX<T>) PersistentCollectionX.super.combine(predicate, op);
    }


    @Override
    default PQueueX<T> toPQueueX() {
        return this;
    }

    @Override
    default <R> PQueueX<R> unit(final Collection<R> col) {
        return fromCollection(col);
    }

    @Override
    default <R> PQueueX<R> unit(final R value) {
        return singleton(value);
    }

    @Override
    default <R> PQueueX<R> unitIterator(final Iterator<R> it) {
        return fromIterable(() -> it);
    }

    @Override
    default <R> PQueueX<R> emptyUnit() {
        return empty();
    }

    @Override
    default ReactiveSeq<T> stream() {

        return ReactiveSeq.fromIterable(this);
    }

    default PQueue<T> toPSet() {
        return this;
    }

    @Override
    default <X> PQueueX<X> from(final Collection<X> col) {
        return fromCollection(col);
    }

    @Override
    default <T> Reducer<PQueue<T>> monoid() {
        return Reducers.toPQueue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PSet#plus(java.lang.Object)
     */
    @Override
    public PQueueX<T> plus(T e);

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PSet#plusAll(java.util.Collection)
     */
    @Override
    public PQueueX<T> plusAll(Collection<? extends T> list);

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PSet#minus(java.lang.Object)
     */
    @Override
    public PQueueX<T> minus(Object e);

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PSet#minusAll(java.util.Collection)
     */
    @Override
    public PQueueX<T> minusAll(Collection<?> list);

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * reverse()
     */
    @Override
    default PQueueX<T> reverse() {
        return (PQueueX<T>) PersistentCollectionX.super.reverse();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * filter(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> filter(final Predicate<? super T> pred) {
        return (PQueueX<T>) PersistentCollectionX.super.filter(pred);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * map(java.util.function.Function)
     */
    @Override
    default <R> PQueueX<R> map(final Function<? super T, ? extends R> mapper) {
        return (PQueueX<R>) PersistentCollectionX.super.map(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * flatMap(java.util.function.Function)
     */
    @Override
    default <R> PQueueX<R> flatMap(final Function<? super T, ? extends Iterable<? extends R>> mapper) {
        return (PQueueX<R>) PersistentCollectionX.super.flatMap(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * limit(long)
     */
    @Override
    default PQueueX<T> limit(final long num) {
        return (PQueueX<T>) PersistentCollectionX.super.limit(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * skip(long)
     */
    @Override
    default PQueueX<T> skip(final long num) {
        return (PQueueX<T>) PersistentCollectionX.super.skip(num);
    }

    @Override
    default PQueueX<T> takeRight(final int num) {
        return (PQueueX<T>) PersistentCollectionX.super.takeRight(num);
    }

    @Override
    default PQueueX<T> dropRight(final int num) {
        return (PQueueX<T>) PersistentCollectionX.super.dropRight(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * takeWhile(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> takeWhile(final Predicate<? super T> p) {
        return (PQueueX<T>) PersistentCollectionX.super.takeWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * dropWhile(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> dropWhile(final Predicate<? super T> p) {
        return (PQueueX<T>) PersistentCollectionX.super.dropWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * takeUntil(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> takeUntil(final Predicate<? super T> p) {
        return (PQueueX<T>) PersistentCollectionX.super.takeUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * dropUntil(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> dropUntil(final Predicate<? super T> p) {
        return (PQueueX<T>) PersistentCollectionX.super.dropUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * trampoline(java.util.function.Function)
     */
    @Override
    default <R> PQueueX<R> trampoline(final Function<? super T, ? extends Trampoline<? extends R>> mapper) {
        return (PQueueX<R>) PersistentCollectionX.super.trampoline(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * slice(long, long)
     */
    @Override
    default PQueueX<T> slice(final long from, final long to) {
        return (PQueueX<T>) PersistentCollectionX.super.slice(from, to);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * sorted(java.util.function.Function)
     */
    @Override
    default <U extends Comparable<? super U>> PQueueX<T> sorted(final Function<? super T, ? extends U> function) {
        return (PQueueX<T>) PersistentCollectionX.super.sorted(function);
    }

    @Override
    default PQueueX<ListX<T>> grouped(final int groupSize) {
        return (PQueueX<ListX<T>>) PersistentCollectionX.super.grouped(groupSize);
    }

    @Override
    default <K, A, D> PQueueX<Tuple2<K, D>> grouped(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream) {
        return (PQueueX) PersistentCollectionX.super.grouped(classifier, downstream);
    }

    @Override
    default <K> PQueueX<Tuple2<K, Seq<T>>> grouped(final Function<? super T, ? extends K> classifier) {
        return (PQueueX) PersistentCollectionX.super.grouped(classifier);
    }

    @Override
    default <U> PQueueX<Tuple2<T, U>> zip(final Iterable<? extends U> other) {
        return (PQueueX) PersistentCollectionX.super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    default <U, R> PQueueX<R> zip(final Iterable<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (PQueueX<R>) PersistentCollectionX.super.zip(other, zipper);
    }

    @Override
    default <U, R> PQueueX<R> zip(final Stream<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (PQueueX<R>) PersistentCollectionX.super.zip(other, zipper);
    }

    @Override
    default <U, R> PQueueX<R> zip(final Seq<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (PQueueX<R>) PersistentCollectionX.super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * permutations()
     */
    @Override
    default PQueueX<ReactiveSeq<T>> permutations() {

        return (PQueueX<ReactiveSeq<T>>) PersistentCollectionX.super.permutations();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * combinations(int)
     */
    @Override
    default PQueueX<ReactiveSeq<T>> combinations(final int size) {

        return (PQueueX<ReactiveSeq<T>>) PersistentCollectionX.super.combinations(size);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * combinations()
     */
    @Override
    default PQueueX<ReactiveSeq<T>> combinations() {

        return (PQueueX<ReactiveSeq<T>>) PersistentCollectionX.super.combinations();
    }

    @Override
    default PQueueX<ListX<T>> sliding(final int windowSize) {
        return (PQueueX<ListX<T>>) PersistentCollectionX.super.sliding(windowSize);
    }

    @Override
    default PQueueX<ListX<T>> sliding(final int windowSize, final int increment) {
        return (PQueueX<ListX<T>>) PersistentCollectionX.super.sliding(windowSize, increment);
    }

    @Override
    default PQueueX<T> scanLeft(final Monoid<T> monoid) {
        return (PQueueX<T>) PersistentCollectionX.super.scanLeft(monoid);
    }

    @Override
    default <U> PQueueX<U> scanLeft(final U seed, final BiFunction<? super U, ? super T, ? extends U> function) {
        return (PQueueX<U>) PersistentCollectionX.super.scanLeft(seed, function);
    }

    @Override
    default PQueueX<T> scanRight(final Monoid<T> monoid) {
        return (PQueueX<T>) PersistentCollectionX.super.scanRight(monoid);
    }

    @Override
    default <U> PQueueX<U> scanRight(final U identity, final BiFunction<? super T, ? super U, ? extends U> combiner) {
        return (PQueueX<U>) PersistentCollectionX.super.scanRight(identity, combiner);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * plusInOrder(java.lang.Object)
     */
    @Override
    default PQueueX<T> plusInOrder(final T e) {

        return (PQueueX<T>) PersistentCollectionX.super.plusInOrder(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * cycle(int)
     */
    @Override
    default PQueueX<T> cycle(final int times) {

        return (PQueueX<T>) PersistentCollectionX.super.cycle(times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * cycle(com.aol.cyclops.sequence.Monoid, int)
     */
    @Override
    default PQueueX<T> cycle(final Monoid<T> m, final int times) {

        return (PQueueX<T>) PersistentCollectionX.super.cycle(m, times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * cycleWhile(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> cycleWhile(final Predicate<? super T> predicate) {

        return (PQueueX<T>) PersistentCollectionX.super.cycleWhile(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * cycleUntil(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> cycleUntil(final Predicate<? super T> predicate) {

        return (PQueueX<T>) PersistentCollectionX.super.cycleUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zip(java.util.stream.Stream)
     */
    @Override
    default <U> PQueueX<Tuple2<T, U>> zip(final Stream<? extends U> other) {

        return (PQueueX) PersistentCollectionX.super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zip(org.jooq.lambda.Seq)
     */
    @Override
    default <U> PQueueX<Tuple2<T, U>> zip(final Seq<? extends U> other) {

        return (PQueueX) PersistentCollectionX.super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zip3(java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    default <S, U> PQueueX<Tuple3<T, S, U>> zip3(final Stream<? extends S> second, final Stream<? extends U> third) {

        return (PQueueX) PersistentCollectionX.super.zip3(second, third);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zip4(java.util.stream.Stream, java.util.stream.Stream,
     * java.util.stream.Stream)
     */
    @Override
    default <T2, T3, T4> PQueueX<Tuple4<T, T2, T3, T4>> zip4(final Stream<? extends T2> second, final Stream<? extends T3> third,
            final Stream<? extends T4> fourth) {

        return (PQueueX) PersistentCollectionX.super.zip4(second, third, fourth);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * zipWithIndex()
     */
    @Override
    default PQueueX<Tuple2<T, Long>> zipWithIndex() {

        return (PQueueX<Tuple2<T, Long>>) PersistentCollectionX.super.zipWithIndex();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * distinct()
     */
    @Override
    default PQueueX<T> distinct() {

        return (PQueueX<T>) PersistentCollectionX.super.distinct();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * sorted()
     */
    @Override
    default PQueueX<T> sorted() {

        return (PQueueX<T>) PersistentCollectionX.super.sorted();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * sorted(java.util.Comparator)
     */
    @Override
    default PQueueX<T> sorted(final Comparator<? super T> c) {

        return (PQueueX<T>) PersistentCollectionX.super.sorted(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * skipWhile(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> skipWhile(final Predicate<? super T> p) {

        return (PQueueX<T>) PersistentCollectionX.super.skipWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * skipUntil(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> skipUntil(final Predicate<? super T> p) {

        return (PQueueX<T>) PersistentCollectionX.super.skipUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * limitWhile(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> limitWhile(final Predicate<? super T> p) {

        return (PQueueX<T>) PersistentCollectionX.super.limitWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * limitUntil(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> limitUntil(final Predicate<? super T> p) {

        return (PQueueX<T>) PersistentCollectionX.super.limitUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * intersperse(java.lang.Object)
     */
    @Override
    default PQueueX<T> intersperse(final T value) {

        return (PQueueX<T>) PersistentCollectionX.super.intersperse(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * shuffle()
     */
    @Override
    default PQueueX<T> shuffle() {

        return (PQueueX<T>) PersistentCollectionX.super.shuffle();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * skipLast(int)
     */
    @Override
    default PQueueX<T> skipLast(final int num) {

        return (PQueueX<T>) PersistentCollectionX.super.skipLast(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * limitLast(int)
     */
    @Override
    default PQueueX<T> limitLast(final int num) {

        return (PQueueX<T>) PersistentCollectionX.super.limitLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.OnEmptySwitch#onEmptySwitch(java.util.function.Supplier)
     */
    @Override
    default PQueueX<T> onEmptySwitch(final Supplier<? extends PQueue<T>> supplier) {
        if (isEmpty())
            return PQueueX.fromIterable(supplier.get());
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * onEmpty(java.lang.Object)
     */
    @Override
    default PQueueX<T> onEmpty(final T value) {

        return (PQueueX<T>) PersistentCollectionX.super.onEmpty(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * onEmptyGet(java.util.function.Supplier)
     */
    @Override
    default PQueueX<T> onEmptyGet(final Supplier<? extends T> supplier) {

        return (PQueueX<T>) PersistentCollectionX.super.onEmptyGet(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    default <X extends Throwable> PQueueX<T> onEmptyThrow(final Supplier<? extends X> supplier) {

        return (PQueueX<T>) PersistentCollectionX.super.onEmptyThrow(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * shuffle(java.util.Random)
     */
    @Override
    default PQueueX<T> shuffle(final Random random) {

        return (PQueueX<T>) PersistentCollectionX.super.shuffle(random);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * ofType(java.lang.Class)
     */
    @Override
    default <U> PQueueX<U> ofType(final Class<? extends U> type) {

        return (PQueueX<U>) PersistentCollectionX.super.ofType(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * filterNot(java.util.function.Predicate)
     */
    @Override
    default PQueueX<T> filterNot(final Predicate<? super T> fn) {

        return (PQueueX<T>) PersistentCollectionX.super.filterNot(fn);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * notNull()
     */
    @Override
    default PQueueX<T> notNull() {

        return (PQueueX<T>) PersistentCollectionX.super.notNull();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * removeAll(java.util.stream.Stream)
     */
    @Override
    default PQueueX<T> removeAll(final Stream<? extends T> stream) {

        return (PQueueX<T>) PersistentCollectionX.super.removeAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * removeAll(java.lang.Iterable)
     */
    @Override
    default PQueueX<T> removeAll(final Iterable<? extends T> it) {

        return (PQueueX<T>) PersistentCollectionX.super.removeAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * removeAll(java.lang.Object[])
     */
    @Override
    default PQueueX<T> removeAll(final T... values) {

        return (PQueueX<T>) PersistentCollectionX.super.removeAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * retainAll(java.lang.Iterable)
     */
    @Override
    default PQueueX<T> retainAll(final Iterable<? extends T> it) {

        return (PQueueX<T>) PersistentCollectionX.super.retainAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * retainAll(java.util.stream.Stream)
     */
    @Override
    default PQueueX<T> retainAll(final Stream<? extends T> seq) {

        return (PQueueX<T>) PersistentCollectionX.super.retainAll(seq);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * retainAll(java.lang.Object[])
     */
    @Override
    default PQueueX<T> retainAll(final T... values) {

        return (PQueueX<T>) PersistentCollectionX.super.retainAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * cast(java.lang.Class)
     */
    @Override
    default <U> PQueueX<U> cast(final Class<? extends U> type) {

        return (PQueueX<U>) PersistentCollectionX.super.cast(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.collections.extensions.persistent.PersistentCollectionX#
     * patternMatch(java.lang.Object, java.util.function.Function)
     */
    @Override
    default <R> PQueueX<R> patternMatch(final Function<CheckValue1<T, R>, CheckValue1<T, R>> case1, final Supplier<? extends R> otherwise) {

        return (PQueueX<R>) PersistentCollectionX.super.patternMatch(case1, otherwise);
    }

    @Override
    default <C extends Collection<? super T>> PQueueX<C> grouped(final int size, final Supplier<C> supplier) {

        return (PQueueX<C>) PersistentCollectionX.super.grouped(size, supplier);
    }

    @Override
    default PQueueX<ListX<T>> groupedUntil(final Predicate<? super T> predicate) {

        return (PQueueX<ListX<T>>) PersistentCollectionX.super.groupedUntil(predicate);
    }

    @Override
    default PQueueX<ListX<T>> groupedStatefullyUntil(final BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (PQueueX<ListX<T>>) PersistentCollectionX.super.groupedStatefullyUntil(predicate);
    }

    @Override
    default PQueueX<ListX<T>> groupedWhile(final Predicate<? super T> predicate) {

        return (PQueueX<ListX<T>>) PersistentCollectionX.super.groupedWhile(predicate);
    }

    @Override
    default <C extends Collection<? super T>> PQueueX<C> groupedWhile(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (PQueueX<C>) PersistentCollectionX.super.groupedWhile(predicate, factory);
    }

    @Override
    default <C extends Collection<? super T>> PQueueX<C> groupedUntil(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (PQueueX<C>) PersistentCollectionX.super.groupedUntil(predicate, factory);
    }

    @Override
    default PQueueX<T> removeAll(final Seq<? extends T> stream) {

        return (PQueueX<T>) PersistentCollectionX.super.removeAll(stream);
    }

    @Override
    default PQueueX<T> retainAll(final Seq<? extends T> stream) {

        return (PQueueX<T>) PersistentCollectionX.super.retainAll(stream);
    }

}
