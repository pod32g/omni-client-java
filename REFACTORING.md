# Refactoring practice

This project is small and has a good test suite, so it is a safe place to practise refactoring:
changing how code is built without changing what it does.

The code is a faithful port of a Go library, so it has some real "smells". Nothing was planted on
purpose. Each exercise below points at one of them.

## Ground rules

1. **Start green.** Run `./build.sh` and see `44 passed, 0 failed`. Those tests are your safety net.
2. **Take small steps.** Make one change, run `./build.sh`, then commit. If a test goes red, undo
   the step rather than debugging a large change.
3. **Refactoring doesn't change behaviour.** If you have to change what a test *expects*, you are
   changing behaviour. That can be fine, but decide it on purpose and commit it on its own.
4. **Let the IDE help.** Rename, Extract Method, Inline and Change Signature in IntelliJ or VS Code
   are safer than editing by hand.

Line numbers drift as you work, so each exercise names the class and method to look for.

---

## 1. Rename to reveal intent (warm-up)

**Where:** `Target.lastScrapeDuration`, and where `Decoders.targets` builds it.

**Smell:** it is a `double` that holds seconds. The unit appears only in a Javadoc comment.

**Do:** make the type say the unit. Either rename it to `lastScrapeSeconds`, or change the type to
`java.time.Duration` and convert in `Decoders.targets`.

**Talk about:** a field name is part of the public API. Renaming it breaks callers, which is cheap
here and expensive in a published library.

## 2. Extract Method on a long method

**Where:** `OmniClient.send`.

**Smell:** about 60 lines doing four jobs: send the request, read the body under a size limit,
parse the `{status: ...}` envelope, and decode `data`.

**Do:** extract one method per job, for example `readBody`, `parseEnvelope` and `decodeData`.
`parseEnvelope` gets cleaner if it returns a small record such as
`record Envelope(String status, String errorType, String error, Object data, boolean hasData)`
instead of four loose variables.

**Safety net:** `ReviewFixesTest` covers the edge cases: an oversized body, and a non-JSON 502, 401
or 503.

## 3. Remove duplication

**Where:** `OmniClient.query`, `queryRange` and `series`.

**Smell:** each method repeats the same code: `new TreeMap<>()`, then `params.put(key,
List.of(value))`, with `Times.format(...)` when the value is optional.

**Do:** extract a tiny query-parameter builder, for example:

```java
QueryParams.of("query", expr).time("time", time).build()
```

**Safety net:** `QueryTest.queryRangeSendsSortedEncodedParams` and
`MetadataTest.seriesSendsEveryMatcher` pin the exact query string sent, encoding and order
included.

## 4. Replace magic strings

**Where:** the `"/api/v1/..."` paths and the header names in `OmniClient`.

**Do:** collect them as constants, or as an `enum Endpoint` that knows its path. Ask whether the
enum is really clearer than constants. "No, keep it simple" is a fine answer.

## 5. Replace a type code with types

**Where:** `QueryResult`.

**Smell:** a `type` field plus three fields, two of which are always empty. Every caller has to
remember which field goes with which type.

**Do:** turn it into a `sealed interface QueryResult permits VectorResult, MatrixResult,
ScalarResult`. Callers then use a pattern-matching `switch`, and the compiler checks that they
handle every case.

**Talk about:** this changes the public API, so the tests that read `res.vector()` change too. That
is fine when you own every caller. Discuss what you would do if you didn't.

## 6. Make illegal states unrepresentable

**Where:** `PushSeries`, and the "exactly one of value or samples" check in `OmniClient.push`.

**Smell:** a `PushSeries` can be built with both a value and samples, or with neither, and nothing
stops it until `push()` throws at runtime.

**Do:** make `PushSeries` a sealed interface with two records: one holds a single value and one
holds explicit samples. The runtime check in `push()` goes away because a wrong series can no
longer be built.

**Talk about:** the "both" and "neither" cases in `PushTest.pushClientValidation` stop compiling.
Deleting them is correct: the type system now does that test's job.

## 7. Write a characterization test, then fix what it finds

**Where:** `Registry.metricKey`.

**Smell:** a metric's identity is a string glued together from its name and labels, joined with a
separator and `=`. Strings built like that can collide. These two calls end up on the same series
(this was checked):

```java
registry.add("m", Map.of("a=b", "c"), 1);
registry.add("m", Map.of("a", "b=c"), 1);   // both become "m<sep>a=b=c": one series, value 2
```

The Go original has the same bug. It is harmless in practice, because Prometheus label names can't
contain `=`, but nothing in the client checks that.

**Do:**

1. Write a test that pins today's behaviour: the two calls produce **one** series. It passes. This
   is a *characterization test*: it records what the code does, not what it should do. Commit it.
2. Replace the `String` key with `record MetricKey(String name, SortedMap<String, String> labels)`.
   `series()` still has to return a sorted snapshot, so you'll need a `Comparator`. Run the tests:
   the characterization test now **fails**. The change you meant as a refactoring also changed
   behaviour, and the test caught it. Catching that is what characterization tests are for.
3. Decide on purpose. Here the new behaviour is the fix you want, so change the test to expect
   **two** series, and commit with a message that says it fixes a bug.

**Talk about:** a change that turns a test red is not a pure refactoring. Here the red is welcome,
but the commit should still say "fix", not "refactor".

## 8. Move Method, and a design discussion

**Where:** `Decoders`.

**Smell (maybe):** one class knows the JSON shape of every record.

**Do:** move each decoder onto its record, for example `static Target fromJson(Map<String,
Object>)`, and see whether the result reads better.

**Talk about:** keeping records as plain data has value too. There is no single right answer; the
point is to argue it out.

## 9. Tooling refactor: switch to JUnit 5

**Where:** `src/test`.

**Do:** add Maven or Gradle with JUnit 5, and delete `TestRunner`, `Test` and `Assertions`.
`Assertions` already uses JUnit's method names and argument order, so most test files only need
their imports changed.

**Talk about:** the test count must still be 44, so check it before and after.

---

## Bonus: compare the two languages

Open [`../omni-client-go`](../omni-client-go) side by side with this project. The README's "How the
Go idioms became Java" table is a guide. Some design choices came from the language, such as
functional options versus a builder, error values versus exceptions, and a copied `http.Client`
versus an immutable `HttpClient`. Porting is a kind of refactoring too: the behaviour stays the same
while the structure changes completely.
