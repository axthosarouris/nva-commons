package no.unit.nva.testutils;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomDoi;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.collection.IsIn.in;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNot.not;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import nva.commons.doi.DoiValidator;
import org.apache.commons.validator.routines.ISBNValidator;
import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Test;

class RandomDataGeneratorTest {

    public static final int BIG_ENOUGH_NUMBER_TO_PRODUCE_ALl_POSSIBLE_VALUES = 100;

    @Test
    void shouldReturnRandomString() {
        assertThat(RandomDataGenerator.randomString(), is(instanceOf(String.class)));
    }

    @Test
    void shouldReturnRandomNonNegativeInteger() {
        assertThat(RandomDataGenerator.randomInteger(), is(instanceOf(Integer.class)));
        assertThat(RandomDataGenerator.randomInteger(), is(greaterThanOrEqualTo(0)));
    }

    @Test
    void shouldReturnPositiveIntegerSmallerThanSpecifiedBound() {
        assertThat(RandomDataGenerator.randomInteger(1), is(instanceOf(Integer.class)));
        assertThat(RandomDataGenerator.randomInteger(1), is(equalTo(0)));
    }

    @Test
    void shouldReturnValidRandomUri() {
        assertThat(RandomDataGenerator.randomUri(), is(instanceOf(URI.class)));
    }

    @Test
    void shouldReturnValidIsbn10() {
        String isbn10 = RandomDataGenerator.randomIsbn10();
        assertThat(ISBNValidator.getInstance().isValid(isbn10), is(true));
        assertThat(isbn10, is(instanceOf(String.class)));
    }

    @Test
    void shouldReturnValidIsbn13() {
        String isbn13 = RandomDataGenerator.randomIsbn13();
        assertThat(ISBNValidator.getInstance().isValid(isbn13), is(true));
        assertThat(isbn13, is(instanceOf(String.class)));
    }

    @Test
    void shouldReturnRandomElementOfArray() {
        String[] elements = new String[]{"a", "b", "c"};
        String randomElement = RandomDataGenerator.randomElement(elements);
        assertThat(randomElement, is(in(elements)));
    }

    @Test
    void shouldReturnRandomElementOfCollection() {
        List<String> elements = List.of("a", "b", "c");
        String randomElement = RandomDataGenerator.randomElement(elements);
        assertThat(randomElement, is(in(elements)));
    }

    @Test
    void shouldReturnRandomInstantBeforeNow() {
        assertThat(randomInstant(), is(OrderingComparison.lessThanOrEqualTo(Instant.now())));
    }

    @Test
    void shouldReturnRandomInstantAfterSuppliedDate() {
        Instant earliestDate = Instant.parse("2007-12-03T10:15:30.00Z");
        assertThat(randomInstant(earliestDate), is(OrderingComparison.greaterThan(earliestDate)));
    }

    @Test
    void shouldReturnRandomValidDois() {
        URI doiURi = randomDoi();
        assertThat(DoiValidator.validateOffline(doiURi), is(true));
    }

    @Test
    void shouldGenerateValidRandomJsonObject() throws JsonProcessingException {
        String json = randomJson();
        var root = (ObjectNode) objectMapper.readTree(json);
        assertThat(root.fields().hasNext(), is(true));
    }

    @Test
    void shouldReturnRandomBoolean() {
        boolean firstValue = RandomDataGenerator.randomBoolean();
        boolean oppositeValue =
            IntStream.range(0, BIG_ENOUGH_NUMBER_TO_PRODUCE_ALl_POSSIBLE_VALUES).boxed().map(ignored -> randomBoolean())
                .filter(randomValue -> !randomValue.equals(firstValue))
                .findAny()
                .orElse(firstValue);
        assertThat(oppositeValue, is(not(equalTo(firstValue))));
    }
}