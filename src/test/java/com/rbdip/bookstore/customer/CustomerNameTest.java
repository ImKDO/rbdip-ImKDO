package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerNameTest {

    @ParameterizedTest(name = "\"{0}\" -> [{1}] [{2}]")
    @CsvSource(delimiter = '|', value = {
        "Ivan Petrov|Ivan|Petrov",
        "Madonna|Madonna|''",
        "Anna Maria Sidorova|Anna|Maria Sidorova",
        "'  Anna   Maria  Sidorova  '|Anna|Maria  Sidorova",
        "'Ivan\tPetrov'|Ivan|Petrov",
    })
    void firstWordIsFirstNameAndTheRestIsLastName(String fullName, String firstName, String lastName) {
        assertThat(CustomerName.parse(fullName)).isEqualTo(new CustomerName(firstName, lastName));
    }

    @ParameterizedTest(name = "[{0}] [{1}] -> \"{2}\"")
    @CsvSource(delimiter = '|', value = {
        "Ivan|Petrov|Ivan Petrov",
        "Madonna|''|Madonna",
        "Anna|Maria Sidorova|Anna Maria Sidorova",
    })
    void fullNameJoinsPartsWithSingleSpace(String firstName, String lastName, String fullName) {
        assertThat(new CustomerName(firstName, lastName).fullName()).isEqualTo(fullName);
    }

    @ParameterizedTest
    @CsvSource({"Ivan Petrov", "Madonna", "Anna Maria Sidorova"})
    void parsingNormalizedFullNameIsLossless(String fullName) {
        assertThat(CustomerName.parse(fullName).fullName()).isEqualTo(fullName);
    }
}
