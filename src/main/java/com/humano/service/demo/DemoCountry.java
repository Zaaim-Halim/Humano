package com.humano.service.demo;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * How a demo company looks in a country that has a shipped payroll template: pay in the local
 * currency at local salary levels, local cities and phone numbers, the template enabled with
 * plausible employer settings, and an employee profile for everyone — so the demo's payroll runs
 * through the statutory template instead of the generic sample rules.
 * <p>
 * Employer and employee values are illustrative (a demo company's), not any real employer's
 * notices: e.g. the German health-fund surcharge is the 2026 average and the U2 rate a typical one.
 * Draws come from the caller's own {@link Random}, so the base company (names, dates, levels) stays
 * the same in every country.
 */
public record DemoCountry(
    CountryCode country,
    CurrencyCode currency,
    String templateId,
    BigDecimal salaryFactor,
    BigDecimal salaryStep,
    Map<String, String> employerSettings,
    String phonePrefix,
    String[][] cities
) {
    /** The demo localisation for {@code country}, when one exists. */
    public static Optional<DemoCountry> of(CountryCode country) {
        return Optional.ofNullable(
            switch (country) {
                case US -> new DemoCountry(
                    CountryCode.US,
                    CurrencyCode.USD,
                    "us-federal-employment",
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    Map.of(),
                    "+1",
                    new String[][] {
                        { "Austin", "TX", "73301" },
                        { "Seattle", "WA", "98101" },
                        { "Denver", "CO", "80202" },
                        { "Boston", "MA", "02108" },
                        { "Chicago", "IL", "60601" },
                    }
                );
                case GB -> new DemoCountry(
                    CountryCode.GB,
                    CurrencyCode.GBP,
                    "gb-paye-nic",
                    new BigDecimal("0.75"),
                    BigDecimal.TEN,
                    Map.of(),
                    "+44",
                    new String[][] {
                        { "London", "Greater London", "EC1A 1BB" },
                        { "Manchester", "Greater Manchester", "M1 1AE" },
                        { "Bristol", "Bristol", "BS1 4DJ" },
                        { "Leeds", "West Yorkshire", "LS1 4AP" },
                        { "Edinburgh", "Scotland", "EH1 1YZ" },
                    }
                );
                case NL -> new DemoCountry(
                    CountryCode.NL,
                    CurrencyCode.EUR,
                    "nl-employment",
                    new BigDecimal("0.75"),
                    BigDecimal.TEN,
                    // ~50 staff at these salaries: not a small employer (premieloon 2024 > € 1.082.500); Whk: the 2026 average.
                    Map.of("aofPremium", "HIGH", "whkRate", "0.0152"),
                    "+31",
                    new String[][] {
                        { "Amsterdam", "Noord-Holland", "1012 JS" },
                        { "Rotterdam", "Zuid-Holland", "3011 AD" },
                        { "Utrecht", "Utrecht", "3511 LX" },
                        { "Eindhoven", "Noord-Brabant", "5611 EM" },
                        { "Den Haag", "Zuid-Holland", "2511 CV" },
                    }
                );
                case DE -> new DemoCountry(
                    CountryCode.DE,
                    CurrencyCode.EUR,
                    "de-employment",
                    new BigDecimal("0.75"),
                    BigDecimal.TEN,
                    // Betriebsstätte in Berlin; more than 30 employees, so no U1.
                    Map.of("state", "BE", "u1Participation", "false"),
                    "+49",
                    new String[][] {
                        { "Berlin", "Berlin", "10115" },
                        { "Potsdam", "Brandenburg", "14467" },
                        { "Berlin", "Berlin", "10437" },
                        { "Berlin", "Berlin", "12043" },
                        { "Berlin", "Berlin", "10969" },
                    }
                );
                case BG -> new DemoCountry(
                    CountryCode.BG,
                    CurrencyCode.EUR,
                    "bg-employment",
                    new BigDecimal("0.35"),
                    BigDecimal.TEN,
                    // KID-2025 62: computer programming and consultancy.
                    Map.of("kidCode", "62"),
                    "+359",
                    new String[][] {
                        { "Sofia", "Sofia City", "1000" },
                        { "Plovdiv", "Plovdiv", "4000" },
                        { "Varna", "Varna", "9000" },
                        { "Burgas", "Burgas", "8000" },
                        { "Sofia", "Sofia City", "1404" },
                    }
                );
                case AL -> new DemoCountry(
                    CountryCode.AL,
                    CurrencyCode.ALL,
                    "al-employment",
                    BigDecimal.valueOf(35),
                    BigDecimal.valueOf(500),
                    Map.of(),
                    "+355",
                    new String[][] {
                        { "Tirana", "Tirana", "1001" },
                        { "Durrës", "Durrës", "2001" },
                        { "Shkodër", "Shkodër", "4001" },
                        { "Vlorë", "Vlorë", "9401" },
                        { "Tirana", "Tirana", "1019" },
                    }
                );
                default -> null;
            }
        );
    }

    /** A sample-company salary (US dollars a month) at local levels, rounded to the country's step. */
    public BigDecimal salary(BigDecimal usd) {
        return usd.multiply(salaryFactor).divide(salaryStep, 0, RoundingMode.HALF_UP).multiply(salaryStep);
    }

    /** A local phone number from two draws (the same number of draws as the US format, so later draws don't shift). */
    public String phone(int a, int b) {
        return String.format("%s %03d %04d", phonePrefix, a, b);
    }

    /**
     * The employee's template attributes: what a payroll administrator would enter from the tax card
     * or the employee's declarations.
     *
     * @param r         the localisation's own random draws
     * @param level     job level (INTERN, JUNIOR, MID, SENIOR, LEAD, PRINCIPAL)
     * @param birthDate date of birth
     */
    public Map<String, String> employeeAttributes(Random r, String level, LocalDate birthDate) {
        Map<String, String> a = new HashMap<>();
        boolean intern = "INTERN".equals(level);
        switch (country) {
            case US -> {
                int s = r.nextInt(10);
                a.put("filingStatus", s < 5 ? "SINGLE" : s < 9 ? "MARRIED_FILING_JOINTLY" : "HEAD_OF_HOUSEHOLD");
                if (!"SINGLE".equals(a.get("filingStatus")) && r.nextInt(2) == 0) {
                    a.put("dependentsAmount", String.valueOf(2200 * (1 + r.nextInt(3))));
                }
                if (r.nextInt(8) == 0) a.put("multipleJobs", "true");
            }
            case GB -> {
                int s = r.nextInt(20);
                a.put("taxCode", s == 0 ? "S1257L" : s == 1 ? "1100L" : "1257L");
                a.put("niCategory", "A");
                // Starters declared no earlier pay this tax year (no P45), so the cumulative code starts at zero.
                a.put("broughtForwardTaxYear", "2026");
                a.put("broughtForwardPay", "0");
                a.put("broughtForwardTax", "0");
            }
            case NL -> {
                a.put("applyLoonheffingskorting", "true");
                // Every demo contract is a written permanent one (Handboek §7.4: lage premie).
                a.put("awfPremium", "LOW");
            }
            case DE -> {
                int s = r.nextInt(10);
                a.put("taxClass", s < 6 ? "1" : s < 8 ? "4" : s < 9 ? "3" : "5");
                if (r.nextInt(2) == 0) a.put("churchMember", "true");
                int children = "1".equals(a.get("taxClass")) ? (r.nextInt(5) == 0 ? 1 : 0) : r.nextInt(4);
                if (children > 0) {
                    a.put("pvParent", "true");
                    a.put("pvChildrenUnder25", String.valueOf(children));
                    if (!"5".equals(a.get("taxClass"))) a.put("childAllowances", String.valueOf(children) + ".0");
                } else if (birthDate.getYear() < 1990 && r.nextInt(3) == 0) {
                    // A parent whose children are grown up: no surcharge, no reductions.
                    a.put("pvParent", "true");
                }
                a.put("healthFundAdditionalRate", "2.90");
                a.put("employmentType", "REGULAR");
                a.put("u2Rate", "0.30");
            }
            case BG -> {
                // НКПД groups: managers 1, specialists 2, technicians and interns 3.
                a.put(
                    "qualificationGroup",
                    switch (level) {
                        case "LEAD", "PRINCIPAL" -> "1";
                        case "INTERN" -> "3";
                        default -> "2";
                    }
                );
                a.put("pensionFund", r.nextInt(10) == 0 ? "DOO" : "UPF");
            }
            case AL -> {
                a.put("personalStatusDeclaration", "SIGNED_WITH_THIS_EMPLOYER");
                if (intern) a.put("contractHours", "LESS_THAN_87_HOURS");
            }
            default -> {}
        }
        return a;
    }
}
