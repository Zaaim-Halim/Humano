package com.humano.web.rest.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.shared.Country;
import com.humano.repository.payroll.CountryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CountryResource}: the country picker's list, sorted by name. */
class CountryResourceTest {

    @Test
    void listsCountriesByNameWithTheirCodes() {
        CountryRepository repository = mock(CountryRepository.class);
        when(repository.findAll()).thenReturn(
            List.of(new Country().code(CountryCode.ES).name("Spain"), new Country().code(CountryCode.EE).name("Estonia"))
        );

        List<CountryResource.CountryView> countries = new CountryResource(repository).list().getBody();

        assertThat(countries).extracting(CountryResource.CountryView::code).containsExactly("EE", "ES");
        assertThat(countries).extracting(CountryResource.CountryView::name).containsExactly("Estonia", "Spain");
    }
}
