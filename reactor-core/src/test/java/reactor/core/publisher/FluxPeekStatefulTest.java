/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.Scannable;
import reactor.test.subscriber.AssertSubscriber;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class FluxPeekStatefulTest {

	@Test(expected = NullPointerException.class)
	public void nullSource() {
		new FluxPeekStateful<>(null, null,null, null, null);
	}

	@Test
	public void normal() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		AtomicReference<Integer> onNext = new AtomicReference<>();
		AtomicReference<Throwable> onError = new AtomicReference<>();
		AtomicBoolean onComplete = new AtomicBoolean();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		new FluxPeekStateful<>(Flux.just(1, 2).hide(),
				() -> {
					seedCount.increment();
					return state;
				},
				(v, st) -> {
					onNext.set(v);
					st.increment();
				},
				(e, st) -> onError.set(e),
				(st) -> onComplete.set(true))
				.subscribe(ts);

		Assert.assertEquals((Integer) 2, onNext.get());
		Assert.assertNull(onError.get());
		Assert.assertTrue(onComplete.get());

		Assert.assertEquals(1, seedCount.intValue());
		Assert.assertEquals(2, state.intValue());
	}

	@Test
	public void error() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		AtomicReference<Integer> onNext = new AtomicReference<>();
		AtomicReference<Throwable> onError = new AtomicReference<>();
		AtomicBoolean onComplete = new AtomicBoolean();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		new FluxPeekStateful<>(new FluxError<Integer>(new RuntimeException("forced " + "failure")),
				() -> {
					seedCount.increment();
					return state;
				},
				(v, st) -> {
					onNext.set(v);
					st.increment();
				},
				(e, st) -> onError.set(e),
				(st) -> onComplete.set(true))
				.subscribe(ts);

		Assert.assertNull(onNext.get());
		Assert.assertTrue(onError.get() instanceof RuntimeException);
		Assert.assertFalse(onComplete.get());
	}

	@Test
	public void empty() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		AtomicReference<Integer> onNext = new AtomicReference<>();
		AtomicReference<Throwable> onError = new AtomicReference<>();
		AtomicBoolean onComplete = new AtomicBoolean();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		new FluxPeekStateful<>(Flux.<Integer>empty(),
				() -> {
					seedCount.increment();
					return state;
				},
				(v, st) -> {
					onNext.set(v);
					st.increment();
				},
				(e, st) -> onError.set(e),
				(st) -> onComplete.set(true))
				.subscribe(ts);

		Assert.assertNull(onNext.get());
		Assert.assertNull(onError.get());
		Assert.assertTrue(onComplete.get());
	}

	@Test
	public void never() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		AtomicReference<Integer> onNext = new AtomicReference<>();
		AtomicReference<Throwable> onError = new AtomicReference<>();
		AtomicBoolean onComplete = new AtomicBoolean();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		new FluxPeekStateful<>(Flux.<Integer>never(),
				() -> {
					seedCount.increment();
					return state;
				},
				(v, st) -> {
					onNext.set(v);
					st.increment();
				},
				(e, st) -> onError.set(e),
				(st) -> onComplete.set(true))
				.subscribe(ts);

		Assert.assertNull(onNext.get());
		Assert.assertNull(onError.get());
		Assert.assertFalse(onComplete.get());
	}

	@Test
	public void nextCallbackError() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		Throwable err = new Exception("test");

		new FluxPeekStateful<>(Flux.just(1).hide(),
				() -> {
					seedCount.increment();
					return state;
				},
				(v, s) -> {
					s.increment();
					throw Exceptions.propagate(err);
				},
				null, null)
				.subscribe(ts);

		//nominal error path (DownstreamException)
		ts.assertErrorMessage("test");
		Assert.assertEquals(1, seedCount.intValue());
		Assert.assertEquals(1, state.intValue());
	}

	@Test
	public void nextCallbackBubbleError() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		Throwable err = new Exception("test");

		try {
			new FluxPeekStateful<>(Flux.just(1).hide(),
					() -> {
						seedCount.increment();
						return state;
					},
					(v, s) -> {
						s.increment();
						throw Exceptions.bubble(err);
					}, null, null)
					.subscribe(ts);

			fail();
		}
		catch (Exception e) {
			Assert.assertTrue(Exceptions.unwrap(e) == err);
			Assert.assertEquals(1, seedCount.intValue());
			Assert.assertEquals(1, state.intValue());
		}
	}

	@Test
	public void completeCallbackError() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		Throwable err = new Exception("test");

		new FluxPeekStateful<>(Flux.just(1).hide(),
				() -> {
					seedCount.increment();
					return state;
				},
				null,
				null,
				(s) -> {
					s.increment();
					throw Exceptions.propagate(err);
				})
				.subscribe(ts);

		ts.assertErrorMessage("test");
		Assert.assertEquals(1, seedCount.intValue());
		Assert.assertEquals(1, state.intValue());
	}

	@Test
	public void errorCallbackError() {
		AssertSubscriber<String> ts = AssertSubscriber.create();
		LongAdder seedCount = new LongAdder();
		LongAdder state = new LongAdder();

		IllegalStateException err = new IllegalStateException("test");

		FluxPeekStateful<String, LongAdder> flux = new FluxPeekStateful<>(
				Flux.error(new IllegalArgumentException("bar")),
				() -> {
					seedCount.increment();
					return state;
				},
				null,
				(e, s) -> {
					s.increment();
					throw err;
				},
				null);

		flux.subscribe(ts);

		ts.assertNoValues();
		ts.assertError(IllegalStateException.class);
		ts.assertErrorWith(e -> e.getSuppressed()[0].getMessage().equals("bar"));

		Assert.assertEquals(1, seedCount.intValue());
		Assert.assertEquals(1, state.intValue());
	}

	@Test
    public void scanSubscriber() {
        CoreSubscriber<Integer> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
        FluxPeekStateful<Integer, String> peek = new FluxPeekStateful<>(Flux.just(1),
        		() -> "", (t, s) -> {},
        		(t, s) -> {}, s -> {});
        FluxPeekStateful.PeekStatefulSubscriber<Integer, String> test =
        		new FluxPeekStateful.PeekStatefulSubscriber<Integer, String>(actual, peek, "");
        Subscription parent = Operators.emptySubscription();
        test.onSubscribe(parent);

        Assertions.assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(parent);
        Assertions.assertThat(test.scan(Scannable.Attr.ACTUAL)).isSameAs(actual);

        Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();
        test.onError(new IllegalStateException("boom"));
        Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isTrue();
    }
}