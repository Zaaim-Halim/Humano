package com.humano.service.payroll.template.extension.de;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GENERATED — do not edit. Transpiled by tools/payroll-templates/de/pap2java.py from the BMF/ITZBund
 * XML pseudocode Lohnsteuer2026 (version 1.0), SHA-256 63d8981646d139eba2f4dd990c13b43c4fb3883b402a5a40cddf253aa7aa96b4:
 * Programmablaufplan für die maschinelle Berechnung der vom Arbeitslohn einzubehaltenden Lohnsteuer,
 * des Solidaritätszuschlags und der Maßstabsteuer für die Kirchenlohnsteuer (BMF-Schreiben vom
 * 12. November 2025, IV C 5 - S 2361/00025/016/028, Anlage 1). Names and statements are the PAP's own.
 * Not thread-safe: use one instance per calculation.
 */
@SuppressWarnings({ "all" })
final class Lohnsteuer2026 {

    // ---- Eingangsparameter
    // 1, wenn die Anwendung des Faktorverfahrens gewählt wurden (nur in Steuerklasse IV)
    int af = 1;
    // Auf die Vollendung des 64. Lebensjahres folgende
    // Kalenderjahr (erforderlich, wenn ALTER1=1)
    int AJAHR = 0;
    // 1, wenn das 64. Lebensjahr zu Beginn des Kalenderjahres vollendet wurde, in dem
    // der Lohnzahlungszeitraum endet (§ 24 a EStG), sonst = 0
    int ALTER1 = 0;
    // Merker für die Vorsorgepauschale
    // 0 = der Arbeitnehmer ist in der Arbeitslosenversicherung pflichtversichert; es gilt die allgemeine Beitragsbemessungsgrenze
    // 1 = wenn nicht 0
    int ALV = 0;
    // eingetragener Faktor mit drei Nachkommastellen
    double f = 1.0;
    // Jahresfreibetrag für die Ermittlung der Lohnsteuer für die sonstigen Bezüge
    // sowie für Vermögensbeteiligungen nach § 19a Absatz 1 und 4 EStG nach Maßgabe der
    // elektronischen Lohnsteuerabzugsmerkmale nach § 39e EStG oder der Eintragung
    // auf der Bescheinigung für den Lohnsteuerabzug 2026 in Cent (ggf. 0)
    BigDecimal JFREIB = BigDecimal.ZERO;
    // Jahreshinzurechnungsbetrag für die Ermittlung der Lohnsteuer für die sonstigen Bezüge
    // sowie für Vermögensbeteiligungen nach § 19a Absatz 1 und 4 EStG nach Maßgabe der
    // elektronischen Lohnsteuerabzugsmerkmale nach § 39e EStG oder der Eintragung auf der
    // Bescheinigung für den Lohnsteuerabzug 2026 in Cent (ggf. 0)
    BigDecimal JHINZU = BigDecimal.ZERO;
    // Voraussichtlicher Jahresarbeitslohn ohne sonstige Bezüge (d.h. auch ohne
    // die zu besteuernden Vorteile bei Vermögensbeteiligungen,
    // § 19a Absatz 4 EStG) in Cent.
    // Anmerkung: Die Eingabe dieses Feldes (ggf. 0) ist erforderlich bei Eingaben zu sonstigen
    // Bezügen (Feld SONSTB).
    // Sind in einem vorangegangenen Abrechnungszeitraum bereits sonstige Bezüge gezahlt worden,
    // so sind sie dem voraussichtlichen Jahresarbeitslohn hinzuzurechnen. Gleiches gilt für zu
    // besteuernde Vorteile bei Vermögensbeteiligungen (§ 19a Absatz 4 EStG).
    BigDecimal JRE4 = BigDecimal.ZERO;
    // In JRE4 enthaltene Entschädigungen nach § 24 Nummer 1 EStG und zu besteuernde
    // Vorteile bei Vermögensbeteiligungen (§ 19a Absatz 4 EStG) in Cent
    BigDecimal JRE4ENT = BigDecimal.ZERO;
    // In JRE4 enthaltene Versorgungsbezüge in Cent (ggf. 0)
    BigDecimal JVBEZ = BigDecimal.ZERO;
    // Merker für die Vorsorgepauschale
    // 0 = der Arbeitnehmer ist in der gesetzlichen Rentenversicherung oder einer
    // berufsständischen Versorgungseinrichtung pflichtversichert oder bei Befreiung von der
    // Versicherungspflicht freiwillig versichert; es gilt die allgemeine Beitragsbemessungsgrenze
    // 1 = wenn nicht 0
    int KRV = 0;
    // Kassenindividueller Zusatzbeitragssatz bei einem gesetzlich krankenversicherten Arbeitnehmer
    // in Prozent (bspw. 2,50 für 2,50 %) mit 2 Dezimalstellen.
    // Es ist der volle Zusatzbeitragssatz anzugeben. Die Aufteilung in Arbeitnehmer- und Arbeitgeber-
    // anteil erfolgt im Programmablauf.
    BigDecimal KVZ = BigDecimal.ZERO;
    // Lohnzahlungszeitraum:
    // 1 = Jahr
    // 2 = Monat
    // 3 = Woche
    // 4 = Tag
    int LZZ = 1;
    // Der als elektronisches Lohnsteuerabzugsmerkmal für den Arbeitgeber nach § 39e EStG festgestellte
    // oder in der Bescheinigung für den Lohnsteuerabzug 2026 eingetragene Freibetrag für den
    // Lohnzahlungszeitraum in Cent
    BigDecimal LZZFREIB = BigDecimal.ZERO;
    // Der als elektronisches Lohnsteuerabzugsmerkmal für den Arbeitgeber nach § 39e EStG festgestellte
    // oder in der Bescheinigung für den Lohnsteuerabzug 2026 eingetragene Hinzurechnungsbetrag für den
    // Lohnzahlungszeitraum in Cent
    BigDecimal LZZHINZU = BigDecimal.ZERO;
    // Nicht zu besteuernde Vorteile bei Vermögensbeteiligungen
    // (§ 19a Absatz 1 Satz 4 EStG) in Cent
    BigDecimal MBV = BigDecimal.ZERO;
    // Dem Arbeitgeber mitgeteilte Beiträge des Arbeitnehmers für eine private
    // Basiskranken- bzw. Pflege-Pflichtversicherung im Sinne des
    // § 10 Absatz 1 Nummer 3 EStG in Cent; der Wert ist unabhängig vom Lohnzahlungszeitraum
    // immer als Monatsbetrag anzugeben
    BigDecimal PKPV = BigDecimal.ZERO;
    // Arbeitgeberzuschuss für eine private Basiskranken- bzw. Pflege-Pflichtversicherung im
    // Sinne des § 10 Absatz 1 Nummer 3 EStG in Cent; der Wert ist unabhängig vom
    // Lohnzahlungszeitraum immer als Monatsbetrag anzugeben
    BigDecimal PKPVAGZ = BigDecimal.ZERO;
    // Krankenversicherung:
    // 0 = gesetzlich krankenversicherte Arbeitnehmer
    // 1 = ausschließlich privat krankenversicherte Arbeitnehmer
    int PKV = 0;
    // Zahl der beim Arbeitnehmer zu berücksichtigenden Beitragsabschläge in der sozialen Pflegeversicherung
    // bei mehr als einem Kind
    // 0 = kein Abschlag
    // 1 = Beitragsabschlag für das 2. Kind
    // 2 = Beitragsabschläge für das 2. und 3. Kind
    // 3 = Beitragsabschläge für 2. bis 4. Kinder
    // 4 = Beitragsabschläge für 2. bis 5. oder mehr Kinder
    BigDecimal PVA = BigDecimal.ZERO;
    // 1, wenn bei der sozialen Pflegeversicherung die Besonderheiten in Sachsen zu berücksichtigen sind bzw.
    // zu berücksichtigen wären
    int PVS = 0;
    // 1, wenn er der Arbeitnehmer den Zuschlag zur sozialen Pflegeversicherung
    // zu zahlen hat
    int PVZ = 0;
    // Religionsgemeinschaft des Arbeitnehmers lt. elektronischer Lohnsteuerabzugsmerkmale oder der
    // Bescheinigung für den Lohnsteuerabzug 2026 (bei keiner Religionszugehörigkeit = 0)
    int R = 0;
    // Steuerpflichtiger Arbeitslohn für den Lohnzahlungszeitraum vor Berücksichtigung des
    // Versorgungsfreibetrags und des Zuschlags zum Versorgungsfreibetrag, des Altersentlastungsbetrags
    // und des als elektronisches Lohnsteuerabzugsmerkmal festgestellten oder in der Bescheinigung für
    // den Lohnsteuerabzug 2026 für den Lohnzahlungszeitraum eingetragenen Freibetrags bzw.
    // Hinzurechnungsbetrags in Cent
    BigDecimal RE4 = BigDecimal.ZERO;
    // Sonstige Bezüge einschließlich zu besteuernde Vorteile bei Vermögensbeteiligungen und Sterbegeld bei Versorgungsbezügen sowie
    // Kapitalauszahlungen/Abfindungen, in Cent (ggf. 0)
    BigDecimal SONSTB = BigDecimal.ZERO;
    // In SONSTB enthaltene Entschädigungen nach § 24 Nummer 1 EStG sowie zu besteuernde Vorteile bei Vermögensbeteiligungen (§ 19a
    // Absatz 4 EStG), in Cent
    BigDecimal SONSTENT = BigDecimal.ZERO;
    // Sterbegeld bei Versorgungsbezügen sowie Kapitalauszahlungen/Abfindungen
    // (in SONSTB enthalten), in Cent
    BigDecimal STERBE = BigDecimal.ZERO;
    // Steuerklasse:
    // 1 = I
    // 2 = II
    // 3 = III
    // 4 = IV
    // 5 = V
    // 6 = VI
    int STKL = 1;
    // In RE4 enthaltene Versorgungsbezüge in Cent (ggf. 0) ggf. unter Berücksichtigung
    // einer geänderten Bemessungsgrundlage nach  § 19 Absatz 2 Satz 10 und 11 EStG
    BigDecimal VBEZ = BigDecimal.ZERO;
    // Versorgungsbezug im Januar 2005 bzw. für den ersten vollen Monat, wenn der
    // Versorgungsbezug erstmalig nach Januar 2005 gewährt  wurde, in Cent
    BigDecimal VBEZM = BigDecimal.ZERO;
    // Voraussichtliche Sonderzahlungen von Versorgungsbezügen im
    // Kalenderjahr des Versorgungsbeginns bei Versorgungsempfängern
    // ohne Sterbegeld, Kapitalauszahlungen/Abfindungen in Cent
    BigDecimal VBEZS = BigDecimal.ZERO;
    // In SONSTB enthaltene Versorgungsbezüge einschließlich Sterbegeld in Cent (ggf. 0)
    BigDecimal VBS = BigDecimal.ZERO;
    // Jahr, in dem der Versorgungsbezug erstmalig gewährt wurde;
    // werden mehrere Versorgungsbezüge gezahlt, wird aus
    // Vereinfachungsgründen für die Berechnung das Jahr des ältesten
    // erstmaligen Bezugs herangezogen; auf die Möglichkeit der
    // getrennten Abrechnung verschiedenartiger Bezüge (§ 39e Absatz 5a
    // EStG) wird im Übrigen verwiesen
    int VJAHR = 0;
    // Zahl der Freibeträge für Kinder (eine Dezimalstelle, nur bei Steuerklassen
    // I, II, III und IV)
    BigDecimal ZKF = BigDecimal.ZERO;
    // Zahl der Monate, für die Versorgungsbezüge gezahlt werden [nur
    // erforderlich bei Jahresberechnung (LZZ = 1)]
    int ZMVB = 0;

    // ---- Ausgangsparameter
    // Bemessungsgrundlage für die Kirchenlohnsteuer in Cent
    BigDecimal BK = BigDecimal.ZERO;
    // Bemessungsgrundlage der sonstigen Bezüge  für die Kirchenlohnsteuer in Cent.
    // Hinweis: Negativbeträge, die aus nicht zu besteuernden Vorteilen bei
    // Vermögensbeteiligungen (§ 19a Absatz 1 Satz 4 EStG) resultieren, mindern BK
    // (maximal bis 0). Der Sonderausgabenabzug für tatsächlich erbrachte Vorsorgeaufwendungen
    // im Rahmen der Veranlagung zur Einkommensteuer bleibt unberührt.
    BigDecimal BKS = BigDecimal.ZERO;
    // Für den Lohnzahlungszeitraum einzubehaltende Lohnsteuer in Cent
    BigDecimal LSTLZZ = BigDecimal.ZERO;
    // Für den Lohnzahlungszeitraum einzubehaltender Solidaritätszuschlag
    // in Cent
    BigDecimal SOLZLZZ = BigDecimal.ZERO;
    // Solidaritätszuschlag für sonstige Bezüge in Cent.
    // Hinweis: Negativbeträge, die aus nicht zu besteuernden Vorteilen bei
    // Vermögensbeteiligungen (§ 19a Absatz 1 Satz 4 EStG) resultieren,
    // mindern SOLZLZZ (maximal bis 0). Der Sonderausgabenabzug für
    // tatsächlich erbrachte Vorsorgeaufwendungen im Rahmen der
    // Veranlagung zur Einkommensteuer bleibt unberührt.
    BigDecimal SOLZS = BigDecimal.ZERO;
    // Lohnsteuer für sonstige Bezüge in Cent
    // Hinweis: Negativbeträge, die aus nicht zu besteuernden Vorteilen bei Vermögensbeteiligungen
    // (§ 19a Absatz 1 Satz 4 EStG) resultieren, mindern LSTLZZ (maximal bis 0). Der
    // Sonderausgabenabzug für tatsächlich erbrachte Vorsorgeaufwendungen im Rahmen der
    // Veranlagung zur Einkommensteuer bleibt unberührt.
    BigDecimal STS = BigDecimal.ZERO;

    // ---- Ausgangsparameter
    // Verbrauchter Freibetrag bei Berechnung des laufenden Arbeitslohns, in Cent
    BigDecimal VFRB = BigDecimal.ZERO;
    // Verbrauchter Freibetrag bei Berechnung des voraussichtlichen Jahresarbeitslohns, in Cent
    BigDecimal VFRBS1 = BigDecimal.ZERO;
    // Verbrauchter Freibetrag bei Berechnung der sonstigen Bezüge, in Cent
    BigDecimal VFRBS2 = BigDecimal.ZERO;
    // Für die weitergehende Berücksichtigung des Steuerfreibetrags nach dem DBA Türkei verfügbares ZVE über
    // dem Grundfreibetrag bei der Berechnung des laufenden Arbeitslohns, in Cent
    BigDecimal WVFRB = BigDecimal.ZERO;
    // Für die weitergehende Berücksichtigung des Steuerfreibetrags nach dem DBA Türkei verfügbares ZVE über dem Grundfreibetrag
    // bei der Berechnung des voraussichtlichen Jahresarbeitslohns, in Cent
    BigDecimal WVFRBO = BigDecimal.ZERO;
    // Für die weitergehende Berücksichtigung des Steuerfreibetrags nach dem DBA Türkei verfügbares ZVE
    // über dem Grundfreibetrag bei der Berechnung der sonstigen Bezüge, in Cent
    BigDecimal WVFRBM = BigDecimal.ZERO;

    // ---- Interne Felder
    // Altersentlastungsbetrag in Euro, Cent (2 Dezimalstellen)
    BigDecimal ALTE = BigDecimal.ZERO;
    // Arbeitnehmer-Pauschbetrag/Werbungskosten-Pauschbetrag in Euro
    BigDecimal ANP = BigDecimal.ZERO;
    // Auf den Lohnzahlungszeitraum entfallender Anteil von Jahreswerten
    // auf ganze Cent abgerundet
    BigDecimal ANTEIL1 = BigDecimal.ZERO;
    // Beitragssatz des Arbeitnehmers zur Arbeitslosenversicherung (4 Dezimalstellen)
    BigDecimal AVSATZAN = BigDecimal.ZERO;
    // Beitragsbemessungsgrenze in der gesetzlichen Krankenversicherung
    // und der sozialen Pflegeversicherung in Euro
    BigDecimal BBGKVPV = BigDecimal.ZERO;
    // Allgemeine Beitragsbemessungsgrenze in der allgemeinen Rentenversicherung und Arbeitslosenversicherung in Euro
    BigDecimal BBGRVALV = BigDecimal.ZERO;
    // Bemessungsgrundlage für Altersentlastungsbetrag in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal BMG = BigDecimal.ZERO;
    // Differenz zwischen ST1 und ST2 in Euro
    BigDecimal DIFF = BigDecimal.ZERO;
    // Entlastungsbetrag für Alleinerziehende in Euro
    BigDecimal EFA = BigDecimal.ZERO;
    // Versorgungsfreibetrag in Euro, Cent (2 Dezimalstellen)
    BigDecimal FVB = BigDecimal.ZERO;
    // Versorgungsfreibetrag in Euro, Cent (2 Dezimalstellen) für die Berechnung
    // der Lohnsteuer beim sonstigen Bezug
    BigDecimal FVBSO = BigDecimal.ZERO;
    // Zuschlag zum Versorgungsfreibetrag in Euro
    BigDecimal FVBZ = BigDecimal.ZERO;
    // Zuschlag zum Versorgungsfreibetrag in Euro für die Berechnung
    // der Lohnsteuer beim sonstigen Bezug
    BigDecimal FVBZSO = BigDecimal.ZERO;
    // Grundfreibetrag in Euro
    BigDecimal GFB = BigDecimal.ZERO;
    // Maximaler Altersentlastungsbetrag in Euro
    BigDecimal HBALTE = BigDecimal.ZERO;
    // Maßgeblicher maximaler Versorgungsfreibetrag in Euro, Cent (2 Dezimalstellen)
    BigDecimal HFVB = BigDecimal.ZERO;
    // Maßgeblicher maximaler Zuschlag zum Versorgungsfreibetrag in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal HFVBZ = BigDecimal.ZERO;
    // Maßgeblicher maximaler Zuschlag zum Versorgungsfreibetrag in Euro, Cent (2 Dezimalstellen)
    // für die Berechnung der Lohnsteuer für den sonstigen Bezug
    BigDecimal HFVBZSO = BigDecimal.ZERO;
    // Zwischenfeld zu X für die Berechnung der Steuer nach § 39b
    // Absatz 2 Satz 7 EStG in Euro
    BigDecimal HOCH = BigDecimal.ZERO;
    // Nummer der Tabellenwerte für Versorgungsparameter
    int J = 0;
    // Jahressteuer nach § 51a EStG, aus der Solidaritätszuschlag und
    // Bemessungsgrundlage für die Kirchenlohnsteuer ermittelt werden in Euro
    BigDecimal JBMG = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechneter LZZFREIB in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal JLFREIB = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnete LZZHINZU in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal JLHINZU = BigDecimal.ZERO;
    // Jahreswert, dessen Anteil für einen Lohnzahlungszeitraum in
    // UPANTEIL errechnet werden soll in Cent
    BigDecimal JW = BigDecimal.ZERO;
    // Nummer der Tabellenwerte für Parameter bei Altersentlastungsbetrag
    int K = 0;
    // Summe der Freibeträge für Kinder in Euro
    BigDecimal KFB = BigDecimal.ZERO;
    // Beitragssatz des Arbeitnehmers zur Krankenversicherung
    // (5 Dezimalstellen)
    BigDecimal KVSATZAN = BigDecimal.ZERO;
    // Kennzahl für die Einkommensteuer-Tabellenart:
    // 1 = Grundtarif
    // 2 = Splittingverfahren
    int KZTAB = 0;
    // Jahreslohnsteuer in Euro
    BigDecimal LSTJAHR = BigDecimal.ZERO;
    // Zwischenfelder der Jahreslohnsteuer in Cent
    BigDecimal LSTOSO = BigDecimal.ZERO;
    BigDecimal LSTSO = BigDecimal.ZERO;
    // Mindeststeuer für die Steuerklassen V und VI in Euro
    BigDecimal MIST = BigDecimal.ZERO;
    // Auf einen Jahreswert hochgerechneter Arbeitgeberzuschuss für eine private Basiskranken-
    // bzw. Pflege-Pflichtversicherung im Sinne des § 10 Absatz 1 Nummer 3 EStG in Euro, Cent (2 Dezimalstellen)
    BigDecimal PKPVAGZJ = BigDecimal.ZERO;
    // Beitragssatz des Arbeitnehmers zur Pflegeversicherung (6 Dezimalstellen)
    BigDecimal PVSATZAN = BigDecimal.ZERO;
    // Beitragssatz des Arbeitnehmers in der allgemeinen gesetzlichen Rentenversicherung (4 Dezimalstellen)
    BigDecimal RVSATZAN = BigDecimal.ZERO;
    // Rechenwert in Gleitkommadarstellung
    BigDecimal RW = BigDecimal.ZERO;
    // Sonderausgaben-Pauschbetrag in Euro
    BigDecimal SAP = BigDecimal.ZERO;
    // Freigrenze für den Solidaritätszuschlag in Euro
    BigDecimal SOLZFREI = BigDecimal.ZERO;
    // Solidaritätszuschlag auf die Jahreslohnsteuer in Euro, Cent (2 Dezimalstellen)
    BigDecimal SOLZJ = BigDecimal.ZERO;
    // Zwischenwert für den Solidaritätszuschlag auf die Jahreslohnsteuer
    // in Euro, Cent (2 Dezimalstellen)
    BigDecimal SOLZMIN = BigDecimal.ZERO;
    // Bemessungsgrundlage des Solidaritätszuschlags zur Prüfung der Freigrenze beim Solidaritätszuschlag für sonstige Bezüge in Euro
    BigDecimal SOLZSBMG = BigDecimal.ZERO;
    // Zu versteuerndes Einkommen für die Ermittlung der
    // Bemessungsgrundlage des Solidaritätszuschlags zur Prüfung der
    // Freigrenze beim Solidaritätszuschlag für sonstige Bezüge in Euro,
    // Cent (2 Dezimalstellen)
    BigDecimal SOLZSZVE = BigDecimal.ZERO;
    // Tarifliche Einkommensteuer in Euro
    BigDecimal ST = BigDecimal.ZERO;
    // Tarifliche Einkommensteuer auf das 1,25-fache ZX in Euro
    BigDecimal ST1 = BigDecimal.ZERO;
    // Tarifliche Einkommensteuer auf das 0,75-fache ZX in Euro
    BigDecimal ST2 = BigDecimal.ZERO;
    // Bemessungsgrundlage für den Versorgungsfreibetrag in Cent
    BigDecimal VBEZB = BigDecimal.ZERO;
    // Bemessungsgrundlage für den Versorgungsfreibetrag in Cent für
    // den sonstigen Bezug
    BigDecimal VBEZBSO = BigDecimal.ZERO;
    // Zwischenfeld zu X für die Berechnung der Steuer nach § 39b
    // Absatz 2 Satz 7 EStG in Euro
    BigDecimal VERGL = BigDecimal.ZERO;
    // Auf den Höchstbetrag begrenzte Beiträge zur Arbeitslosenversicherung
    // einschließlich Kranken- und Pflegeversicherung in Euro, Cent (2 Dezimalstellen)
    BigDecimal VSPHB = BigDecimal.ZERO;
    // Vorsorgepauschale mit Teilbeträgen für die Rentenversicherung
    // sowie die gesetzliche Kranken- und soziale Pflegeversicherung nach
    // fiktiven Beträgen oder ggf. für die private Basiskrankenversicherung
    // und private Pflege-Pflichtversicherung in Euro, Cent (2 Dezimalstellen)
    BigDecimal VSP = BigDecimal.ZERO;
    // Vorsorgepauschale mit Teilbeträgen für die Rentenversicherung sowie auf den Höchstbetrag
    // begrenzten Teilbeträgen für die Arbeitslosen-, Kranken- und Pflegeversicherung in
    // Euro, Cent (2 Dezimalstellen)
    BigDecimal VSPN = BigDecimal.ZERO;
    // Teilbetrag für die Arbeitslosenversicherung bei der Berechnung der
    // Vorsorgepauschale in Euro, Cent (2 Dezimalstellen)
    BigDecimal VSPALV = BigDecimal.ZERO;
    // Vorsorgepauschale mit Teilbeträgen für die gesetzliche Kranken- und soziale Pflegeversicherung
    // nach fiktiven Beträgen oder ggf. für die private Basiskrankenversicherung und private
    // Pflege-Pflichtversicherung in Euro, Cent (2 Dezimalstellen)
    BigDecimal VSPKVPV = BigDecimal.ZERO;
    // Teilbetrag für die Rentenversicherung bei der Berechnung der Vorsorgepauschale
    // in Euro, Cent (2 Dezimalstellen)
    BigDecimal VSPR = BigDecimal.ZERO;
    // Erster Grenzwert in Steuerklasse V/VI in Euro
    BigDecimal W1STKL5 = BigDecimal.ZERO;
    // Zweiter Grenzwert in Steuerklasse V/VI in Euro
    BigDecimal W2STKL5 = BigDecimal.ZERO;
    // Dritter Grenzwert in Steuerklasse V/VI in Euro
    BigDecimal W3STKL5 = BigDecimal.ZERO;
    // Zu versteuerndes Einkommen gem. § 32a Absatz 1 und 5 EStG in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal X = BigDecimal.ZERO;
    // Gem. § 32a Absatz 1 EStG (6 Dezimalstellen)
    BigDecimal Y = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnetes RE4 in Euro, Cent (2 Dezimalstellen)
    // nach Abzug der Freibeträge nach § 39 b Absatz 2 Satz 3 und 4 EStG
    BigDecimal ZRE4 = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnetes RE4 in Euro, Cent (2 Dezimalstellen)
    BigDecimal ZRE4J = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnetes RE4, ggf. nach Abzug der
    // Entschädigungen i.S.d. § 24 Nummer 1 EStG in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal ZRE4VP = BigDecimal.ZERO;
    // Zwischenfeld zu ZRE4VP für die Begrenzung auf die jeweilige
    // Beitragsbemessungsgrenze in Euro, Cent (2 Dezimalstellen)"
    BigDecimal ZRE4VPR = BigDecimal.ZERO;
    // Feste Tabellenfreibeträge (ohne Vorsorgepauschale) in Euro, Cent
    // (2 Dezimalstellen)
    BigDecimal ZTABFB = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnetes VBEZ abzüglich FVB in
    // Euro, Cent (2 Dezimalstellen)
    BigDecimal ZVBEZ = BigDecimal.ZERO;
    // Auf einen Jahreslohn hochgerechnetes VBEZ in Euro, Cent (2 Dezimalstellen)
    BigDecimal ZVBEZJ = BigDecimal.ZERO;
    // Zu versteuerndes Einkommen in Euro, Cent (2 Dezimalstellen)
    BigDecimal ZVE = BigDecimal.ZERO;
    // Zwischenfeld zu X für die Berechnung der Steuer nach § 39b
    // Absatz 2 Satz 7 EStG in Euro
    BigDecimal ZX = BigDecimal.ZERO;
    // Zwischenfeld zu X für die Berechnung der Steuer nach § 39b
    // Absatz 2 Satz 7 EStG in Euro
    BigDecimal ZZX = BigDecimal.ZERO;

    // ---- Konstanten
    // Tabelle für die Prozentsätze des Versorgungsfreibetrags
    static final BigDecimal[] TAB1 = {
        BigDecimal.ZERO,
        BigDecimal.valueOf(0.4),
        BigDecimal.valueOf(0.384),
        BigDecimal.valueOf(0.368),
        BigDecimal.valueOf(0.352),
        BigDecimal.valueOf(0.336),
        BigDecimal.valueOf(0.32),
        BigDecimal.valueOf(0.304),
        BigDecimal.valueOf(0.288),
        BigDecimal.valueOf(0.272),
        BigDecimal.valueOf(0.256),
        BigDecimal.valueOf(0.24),
        BigDecimal.valueOf(0.224),
        BigDecimal.valueOf(0.208),
        BigDecimal.valueOf(0.192),
        BigDecimal.valueOf(0.176),
        BigDecimal.valueOf(0.16),
        BigDecimal.valueOf(0.152),
        BigDecimal.valueOf(0.144),
        BigDecimal.valueOf(0.14),
        BigDecimal.valueOf(0.136),
        BigDecimal.valueOf(0.132),
        BigDecimal.valueOf(0.128),
        BigDecimal.valueOf(0.124),
        BigDecimal.valueOf(0.12),
        BigDecimal.valueOf(0.116),
        BigDecimal.valueOf(0.112),
        BigDecimal.valueOf(0.108),
        BigDecimal.valueOf(0.104),
        BigDecimal.valueOf(0.1),
        BigDecimal.valueOf(0.096),
        BigDecimal.valueOf(0.092),
        BigDecimal.valueOf(0.088),
        BigDecimal.valueOf(0.084),
        BigDecimal.valueOf(0.08),
        BigDecimal.valueOf(0.076),
        BigDecimal.valueOf(0.072),
        BigDecimal.valueOf(0.068),
        BigDecimal.valueOf(0.064),
        BigDecimal.valueOf(0.06),
        BigDecimal.valueOf(0.056),
        BigDecimal.valueOf(0.052),
        BigDecimal.valueOf(0.048),
        BigDecimal.valueOf(0.044),
        BigDecimal.valueOf(0.04),
        BigDecimal.valueOf(0.036),
        BigDecimal.valueOf(0.032),
        BigDecimal.valueOf(0.028),
        BigDecimal.valueOf(0.024),
        BigDecimal.valueOf(0.02),
        BigDecimal.valueOf(0.016),
        BigDecimal.valueOf(0.012),
        BigDecimal.valueOf(0.008),
        BigDecimal.valueOf(0.004),
        BigDecimal.valueOf(0),
    };
    // Tabelle für die Höchstbeträge des Versorgungsfreibetrags
    static final BigDecimal[] TAB2 = {
        BigDecimal.ZERO,
        BigDecimal.valueOf(3000),
        BigDecimal.valueOf(2880),
        BigDecimal.valueOf(2760),
        BigDecimal.valueOf(2640),
        BigDecimal.valueOf(2520),
        BigDecimal.valueOf(2400),
        BigDecimal.valueOf(2280),
        BigDecimal.valueOf(2160),
        BigDecimal.valueOf(2040),
        BigDecimal.valueOf(1920),
        BigDecimal.valueOf(1800),
        BigDecimal.valueOf(1680),
        BigDecimal.valueOf(1560),
        BigDecimal.valueOf(1440),
        BigDecimal.valueOf(1320),
        BigDecimal.valueOf(1200),
        BigDecimal.valueOf(1140),
        BigDecimal.valueOf(1080),
        BigDecimal.valueOf(1050),
        BigDecimal.valueOf(1020),
        BigDecimal.valueOf(990),
        BigDecimal.valueOf(960),
        BigDecimal.valueOf(930),
        BigDecimal.valueOf(900),
        BigDecimal.valueOf(870),
        BigDecimal.valueOf(840),
        BigDecimal.valueOf(810),
        BigDecimal.valueOf(780),
        BigDecimal.valueOf(750),
        BigDecimal.valueOf(720),
        BigDecimal.valueOf(690),
        BigDecimal.valueOf(660),
        BigDecimal.valueOf(630),
        BigDecimal.valueOf(600),
        BigDecimal.valueOf(570),
        BigDecimal.valueOf(540),
        BigDecimal.valueOf(510),
        BigDecimal.valueOf(480),
        BigDecimal.valueOf(450),
        BigDecimal.valueOf(420),
        BigDecimal.valueOf(390),
        BigDecimal.valueOf(360),
        BigDecimal.valueOf(330),
        BigDecimal.valueOf(300),
        BigDecimal.valueOf(270),
        BigDecimal.valueOf(240),
        BigDecimal.valueOf(210),
        BigDecimal.valueOf(180),
        BigDecimal.valueOf(150),
        BigDecimal.valueOf(120),
        BigDecimal.valueOf(90),
        BigDecimal.valueOf(60),
        BigDecimal.valueOf(30),
        BigDecimal.valueOf(0),
    };
    // Tabelle für die Zuschläge zum Versorgungsfreibetrag
    static final BigDecimal[] TAB3 = {
        BigDecimal.ZERO,
        BigDecimal.valueOf(900),
        BigDecimal.valueOf(864),
        BigDecimal.valueOf(828),
        BigDecimal.valueOf(792),
        BigDecimal.valueOf(756),
        BigDecimal.valueOf(720),
        BigDecimal.valueOf(684),
        BigDecimal.valueOf(648),
        BigDecimal.valueOf(612),
        BigDecimal.valueOf(576),
        BigDecimal.valueOf(540),
        BigDecimal.valueOf(504),
        BigDecimal.valueOf(468),
        BigDecimal.valueOf(432),
        BigDecimal.valueOf(396),
        BigDecimal.valueOf(360),
        BigDecimal.valueOf(342),
        BigDecimal.valueOf(324),
        BigDecimal.valueOf(315),
        BigDecimal.valueOf(306),
        BigDecimal.valueOf(297),
        BigDecimal.valueOf(288),
        BigDecimal.valueOf(279),
        BigDecimal.valueOf(270),
        BigDecimal.valueOf(261),
        BigDecimal.valueOf(252),
        BigDecimal.valueOf(243),
        BigDecimal.valueOf(234),
        BigDecimal.valueOf(225),
        BigDecimal.valueOf(216),
        BigDecimal.valueOf(207),
        BigDecimal.valueOf(198),
        BigDecimal.valueOf(189),
        BigDecimal.valueOf(180),
        BigDecimal.valueOf(171),
        BigDecimal.valueOf(162),
        BigDecimal.valueOf(153),
        BigDecimal.valueOf(144),
        BigDecimal.valueOf(135),
        BigDecimal.valueOf(126),
        BigDecimal.valueOf(117),
        BigDecimal.valueOf(108),
        BigDecimal.valueOf(99),
        BigDecimal.valueOf(90),
        BigDecimal.valueOf(81),
        BigDecimal.valueOf(72),
        BigDecimal.valueOf(63),
        BigDecimal.valueOf(54),
        BigDecimal.valueOf(45),
        BigDecimal.valueOf(36),
        BigDecimal.valueOf(27),
        BigDecimal.valueOf(18),
        BigDecimal.valueOf(9),
        BigDecimal.valueOf(0),
    };
    // Tabelle für die Höchstbeträge des Altersentlastungsbetrags
    static final BigDecimal[] TAB4 = {
        BigDecimal.ZERO,
        BigDecimal.valueOf(0.4),
        BigDecimal.valueOf(0.384),
        BigDecimal.valueOf(0.368),
        BigDecimal.valueOf(0.352),
        BigDecimal.valueOf(0.336),
        BigDecimal.valueOf(0.32),
        BigDecimal.valueOf(0.304),
        BigDecimal.valueOf(0.288),
        BigDecimal.valueOf(0.272),
        BigDecimal.valueOf(0.256),
        BigDecimal.valueOf(0.24),
        BigDecimal.valueOf(0.224),
        BigDecimal.valueOf(0.208),
        BigDecimal.valueOf(0.192),
        BigDecimal.valueOf(0.176),
        BigDecimal.valueOf(0.16),
        BigDecimal.valueOf(0.152),
        BigDecimal.valueOf(0.144),
        BigDecimal.valueOf(0.14),
        BigDecimal.valueOf(0.136),
        BigDecimal.valueOf(0.132),
        BigDecimal.valueOf(0.128),
        BigDecimal.valueOf(0.124),
        BigDecimal.valueOf(0.12),
        BigDecimal.valueOf(0.116),
        BigDecimal.valueOf(0.112),
        BigDecimal.valueOf(0.108),
        BigDecimal.valueOf(0.104),
        BigDecimal.valueOf(0.1),
        BigDecimal.valueOf(0.096),
        BigDecimal.valueOf(0.092),
        BigDecimal.valueOf(0.088),
        BigDecimal.valueOf(0.084),
        BigDecimal.valueOf(0.08),
        BigDecimal.valueOf(0.076),
        BigDecimal.valueOf(0.072),
        BigDecimal.valueOf(0.068),
        BigDecimal.valueOf(0.064),
        BigDecimal.valueOf(0.06),
        BigDecimal.valueOf(0.056),
        BigDecimal.valueOf(0.052),
        BigDecimal.valueOf(0.048),
        BigDecimal.valueOf(0.044),
        BigDecimal.valueOf(0.04),
        BigDecimal.valueOf(0.036),
        BigDecimal.valueOf(0.032),
        BigDecimal.valueOf(0.028),
        BigDecimal.valueOf(0.024),
        BigDecimal.valueOf(0.02),
        BigDecimal.valueOf(0.016),
        BigDecimal.valueOf(0.012),
        BigDecimal.valueOf(0.008),
        BigDecimal.valueOf(0.004),
        BigDecimal.valueOf(0),
    };
    // Tabelle fuer die Hächstbeträge des Altersentlastungsbetrags
    static final BigDecimal[] TAB5 = {
        BigDecimal.ZERO,
        BigDecimal.valueOf(1900),
        BigDecimal.valueOf(1824),
        BigDecimal.valueOf(1748),
        BigDecimal.valueOf(1672),
        BigDecimal.valueOf(1596),
        BigDecimal.valueOf(1520),
        BigDecimal.valueOf(1444),
        BigDecimal.valueOf(1368),
        BigDecimal.valueOf(1292),
        BigDecimal.valueOf(1216),
        BigDecimal.valueOf(1140),
        BigDecimal.valueOf(1064),
        BigDecimal.valueOf(988),
        BigDecimal.valueOf(912),
        BigDecimal.valueOf(836),
        BigDecimal.valueOf(760),
        BigDecimal.valueOf(722),
        BigDecimal.valueOf(684),
        BigDecimal.valueOf(665),
        BigDecimal.valueOf(646),
        BigDecimal.valueOf(627),
        BigDecimal.valueOf(608),
        BigDecimal.valueOf(589),
        BigDecimal.valueOf(570),
        BigDecimal.valueOf(551),
        BigDecimal.valueOf(532),
        BigDecimal.valueOf(513),
        BigDecimal.valueOf(494),
        BigDecimal.valueOf(475),
        BigDecimal.valueOf(456),
        BigDecimal.valueOf(437),
        BigDecimal.valueOf(418),
        BigDecimal.valueOf(399),
        BigDecimal.valueOf(380),
        BigDecimal.valueOf(361),
        BigDecimal.valueOf(342),
        BigDecimal.valueOf(323),
        BigDecimal.valueOf(304),
        BigDecimal.valueOf(285),
        BigDecimal.valueOf(266),
        BigDecimal.valueOf(247),
        BigDecimal.valueOf(228),
        BigDecimal.valueOf(209),
        BigDecimal.valueOf(190),
        BigDecimal.valueOf(171),
        BigDecimal.valueOf(152),
        BigDecimal.valueOf(133),
        BigDecimal.valueOf(114),
        BigDecimal.valueOf(95),
        BigDecimal.valueOf(76),
        BigDecimal.valueOf(57),
        BigDecimal.valueOf(38),
        BigDecimal.valueOf(19),
        BigDecimal.valueOf(0),
    };
    // Zahlenkonstanten fuer im Plan oft genutzte BigDecimal Werte
    static final BigDecimal ZAHL1 = BigDecimal.ONE;
    static final BigDecimal ZAHL2 = BigDecimal.valueOf(2);
    static final BigDecimal ZAHL5 = BigDecimal.valueOf(5);
    static final BigDecimal ZAHL7 = BigDecimal.valueOf(7);
    static final BigDecimal ZAHL12 = BigDecimal.valueOf(12);
    static final BigDecimal ZAHL100 = BigDecimal.valueOf(100);
    static final BigDecimal ZAHL360 = BigDecimal.valueOf(360);
    static final BigDecimal ZAHL500 = BigDecimal.valueOf(500);
    static final BigDecimal ZAHL700 = BigDecimal.valueOf(700);
    static final BigDecimal ZAHL1000 = BigDecimal.valueOf(1000);
    static final BigDecimal ZAHL10000 = BigDecimal.valueOf(10000);

    // PROGRAMMABLAUFPLAN 2026
    // Steueruung, PAP Seite 13

    /** The PAP's main program. */
    void main() {
        MPARA();
        MRE4JL();
        VBEZBSO = BigDecimal.ZERO;
        MRE4();
        MRE4ABZ();
        MBERECH();
        MSONST();
    }

    // Zuweisung von Werten für bestimmte Steuer- und Sozialversicherungsparameter  PAP Seite 14

    private void MPARA() {
        BBGRVALV = BigDecimal.valueOf(101400);
        AVSATZAN = BigDecimal.valueOf(0.013);
        RVSATZAN = BigDecimal.valueOf(0.093);
        BBGKVPV = BigDecimal.valueOf(69750);
        KVSATZAN = (KVZ.divide(ZAHL2).divide(ZAHL100)).add(BigDecimal.valueOf(0.07));
        if (PVS == 1) {
            PVSATZAN = BigDecimal.valueOf(0.023);
        } else {
            PVSATZAN = BigDecimal.valueOf(0.018);
        }
        if (PVZ == 1) {
            PVSATZAN = PVSATZAN.add(BigDecimal.valueOf(0.006));
        } else {
            PVSATZAN = PVSATZAN.subtract(PVA.multiply(BigDecimal.valueOf(0.0025)));
        }
        W1STKL5 = BigDecimal.valueOf(14071);
        W2STKL5 = BigDecimal.valueOf(34939);
        W3STKL5 = BigDecimal.valueOf(222260);
        GFB = BigDecimal.valueOf(12348);
        SOLZFREI = BigDecimal.valueOf(20350);
    }

    // Ermittlung des Jahresarbeitslohns nach § 39 b Absatz 2 Satz 2 EStG, PAP Seite 15

    private void MRE4JL() {
        if (LZZ == 1) {
            ZRE4J = RE4.divide(ZAHL100, 2, RoundingMode.DOWN);
            ZVBEZJ = VBEZ.divide(ZAHL100, 2, RoundingMode.DOWN);
            JLFREIB = LZZFREIB.divide(ZAHL100, 2, RoundingMode.DOWN);
            JLHINZU = LZZHINZU.divide(ZAHL100, 2, RoundingMode.DOWN);
        } else {
            if (LZZ == 2) {
                ZRE4J = (RE4.multiply(ZAHL12)).divide(ZAHL100, 2, RoundingMode.DOWN);
                ZVBEZJ = (VBEZ.multiply(ZAHL12)).divide(ZAHL100, 2, RoundingMode.DOWN);
                JLFREIB = (LZZFREIB.multiply(ZAHL12)).divide(ZAHL100, 2, RoundingMode.DOWN);
                JLHINZU = (LZZHINZU.multiply(ZAHL12)).divide(ZAHL100, 2, RoundingMode.DOWN);
            } else {
                if (LZZ == 3) {
                    ZRE4J = (RE4.multiply(ZAHL360)).divide(ZAHL700, 2, RoundingMode.DOWN);
                    ZVBEZJ = (VBEZ.multiply(ZAHL360)).divide(ZAHL700, 2, RoundingMode.DOWN);
                    JLFREIB = (LZZFREIB.multiply(ZAHL360)).divide(ZAHL700, 2, RoundingMode.DOWN);
                    JLHINZU = (LZZHINZU.multiply(ZAHL360)).divide(ZAHL700, 2, RoundingMode.DOWN);
                } else {
                    ZRE4J = (RE4.multiply(ZAHL360)).divide(ZAHL100, 2, RoundingMode.DOWN);
                    ZVBEZJ = (VBEZ.multiply(ZAHL360)).divide(ZAHL100, 2, RoundingMode.DOWN);
                    JLFREIB = (LZZFREIB.multiply(ZAHL360)).divide(ZAHL100, 2, RoundingMode.DOWN);
                    JLHINZU = (LZZHINZU.multiply(ZAHL360)).divide(ZAHL100, 2, RoundingMode.DOWN);
                }
            }
        }
        if (af == 0) {
            f = 1;
        }
    }

    // Freibeträge für Versorgungsbezüge, Altersentlastungsbetrag (§ 39b Absatz 2 Satz 3 EStG), PAP Seite 16

    private void MRE4() {
        if (ZVBEZJ.compareTo(BigDecimal.ZERO) == 0) {
            FVBZ = BigDecimal.ZERO;
            FVB = BigDecimal.ZERO;
            FVBZSO = BigDecimal.ZERO;
            FVBSO = BigDecimal.ZERO;
        } else {
            if (VJAHR < 2006) {
                J = 1;
            } else {
                if (VJAHR < 2058) {
                    J = VJAHR - 2004;
                } else {
                    J = 54;
                }
            }
            if (LZZ == 1) {
                VBEZB = (VBEZM.multiply(BigDecimal.valueOf(ZMVB))).add(VBEZS);
                HFVB = TAB2[J].divide(ZAHL12).multiply(BigDecimal.valueOf(ZMVB)).setScale(0, RoundingMode.UP);
                FVBZ = TAB3[J].divide(ZAHL12).multiply(BigDecimal.valueOf(ZMVB)).setScale(0, RoundingMode.UP);
            } else {
                VBEZB = ((VBEZM.multiply(ZAHL12)).add(VBEZS)).setScale(2, RoundingMode.DOWN);
                HFVB = TAB2[J];
                FVBZ = TAB3[J];
            }
            FVB = ((VBEZB.multiply(TAB1[J]))).divide(ZAHL100).setScale(2, RoundingMode.UP);
            if (FVB.compareTo(HFVB) == 1) {
                FVB = HFVB;
            }
            if (FVB.compareTo(ZVBEZJ) == 1) {
                FVB = ZVBEZJ;
            }
            FVBSO = (FVB.add((VBEZBSO.multiply(TAB1[J])).divide(ZAHL100))).setScale(2, RoundingMode.UP);
            if (FVBSO.compareTo(TAB2[J]) == 1) {
                FVBSO = TAB2[J];
            }
            HFVBZSO = (((VBEZB.add(VBEZBSO)).divide(ZAHL100)).subtract(FVBSO)).setScale(2, RoundingMode.DOWN);
            FVBZSO = (FVBZ.add((VBEZBSO).divide(ZAHL100))).setScale(0, RoundingMode.UP);
            if (FVBZSO.compareTo(HFVBZSO) == 1) {
                FVBZSO = HFVBZSO.setScale(0, RoundingMode.UP);
            }
            if (FVBZSO.compareTo(TAB3[J]) == 1) {
                FVBZSO = TAB3[J];
            }
            HFVBZ = ((VBEZB.divide(ZAHL100)).subtract(FVB)).setScale(2, RoundingMode.DOWN);
            if (FVBZ.compareTo(HFVBZ) == 1) {
                FVBZ = HFVBZ.setScale(0, RoundingMode.UP);
            }
        }
        MRE4ALTE();
    }

    // Altersentlastungsbetrag (§ 39b Absatz 2 Satz 3 EStG), PAP Seite 17

    private void MRE4ALTE() {
        if (ALTER1 == 0) {
            ALTE = BigDecimal.ZERO;
        } else {
            if (AJAHR < 2006) {
                K = 1;
            } else {
                if (AJAHR < 2058) {
                    K = AJAHR - 2004;
                } else {
                    K = 54;
                }
            }
            BMG = ZRE4J.subtract(ZVBEZJ);
            ALTE = (BMG.multiply(TAB4[K])).setScale(0, RoundingMode.UP);
            HBALTE = TAB5[K];
            if (ALTE.compareTo(HBALTE) == 1) {
                ALTE = HBALTE;
            }
        }
    }

    // Ermittlung des Jahresarbeitslohns nach Abzug der Freibeträge nach § 39 b Absatz 2 Satz 3 und 4 EStG, PAP Seite 20

    private void MRE4ABZ() {
        ZRE4 = (ZRE4J.subtract(FVB).subtract(ALTE).subtract(JLFREIB).add(JLHINZU)).setScale(2, RoundingMode.DOWN);
        if (ZRE4.compareTo(BigDecimal.ZERO) == -1) {
            ZRE4 = BigDecimal.ZERO;
        }
        ZRE4VP = ZRE4J;
        ZVBEZ = ZVBEZJ.subtract(FVB).setScale(2, RoundingMode.DOWN);
        if (ZVBEZ.compareTo(BigDecimal.ZERO) == -1) {
            ZVBEZ = BigDecimal.ZERO;
        }
    }

    // Berechnung fuer laufende Lohnzahlungszeitraueme Seite 21

    private void MBERECH() {
        MZTABFB();
        VFRB = ((ANP.add(FVB.add(FVBZ))).multiply(ZAHL100)).setScale(0, RoundingMode.DOWN);
        MLSTJAHR();
        WVFRB = ((ZVE.subtract(GFB)).multiply(ZAHL100)).setScale(0, RoundingMode.DOWN);
        if (WVFRB.compareTo(BigDecimal.ZERO) == -1) {
            WVFRB = BigDecimal.ZERO;
        }
        LSTJAHR = (ST.multiply(BigDecimal.valueOf(f))).setScale(0, RoundingMode.DOWN);
        UPLSTLZZ();
        if (ZKF.compareTo(BigDecimal.ZERO) == 1) {
            ZTABFB = ZTABFB.add(KFB);
            MRE4ABZ();
            MLSTJAHR();
            JBMG = (ST.multiply(BigDecimal.valueOf(f))).setScale(0, RoundingMode.DOWN);
        } else {
            JBMG = LSTJAHR;
        }
        MSOLZ();
    }

    // Ermittlung der festen Tabellenfreibeträge (ohne Vorsorgepauschale), PAP Seite 22

    private void MZTABFB() {
        ANP = BigDecimal.ZERO;
        if (ZVBEZ.compareTo(BigDecimal.ZERO) >= 0 && ZVBEZ.compareTo(FVBZ) == -1) {
            FVBZ = BigDecimal.valueOf(ZVBEZ.longValue());
        }
        if (STKL < 6) {
            if (ZVBEZ.compareTo(BigDecimal.ZERO) == 1) {
                if ((ZVBEZ.subtract(FVBZ)).compareTo(BigDecimal.valueOf(102)) == -1) {
                    ANP = (ZVBEZ.subtract(FVBZ)).setScale(0, RoundingMode.UP);
                } else {
                    ANP = BigDecimal.valueOf(102);
                }
            }
        } else {
            FVBZ = BigDecimal.ZERO;
            FVBZSO = BigDecimal.ZERO;
        }
        if (STKL < 6) {
            if (ZRE4.compareTo(ZVBEZ) == 1) {
                if (ZRE4.subtract(ZVBEZ).compareTo(BigDecimal.valueOf(1230)) == -1) {
                    ANP = ANP.add(ZRE4).subtract(ZVBEZ).setScale(0, RoundingMode.UP);
                } else {
                    ANP = ANP.add(BigDecimal.valueOf(1230));
                }
            }
        }
        KZTAB = 1;
        if (STKL == 1) {
            SAP = BigDecimal.valueOf(36);
            KFB = (ZKF.multiply(BigDecimal.valueOf(9756))).setScale(0, RoundingMode.DOWN);
        } else {
            if (STKL == 2) {
                EFA = BigDecimal.valueOf(4260);
                SAP = BigDecimal.valueOf(36);
                KFB = (ZKF.multiply(BigDecimal.valueOf(9756))).setScale(0, RoundingMode.DOWN);
            } else {
                if (STKL == 3) {
                    KZTAB = 2;
                    SAP = BigDecimal.valueOf(36);
                    KFB = (ZKF.multiply(BigDecimal.valueOf(9756))).setScale(0, RoundingMode.DOWN);
                } else {
                    if (STKL == 4) {
                        SAP = BigDecimal.valueOf(36);
                        KFB = (ZKF.multiply(BigDecimal.valueOf(4878))).setScale(0, RoundingMode.DOWN);
                    } else {
                        if (STKL == 5) {
                            SAP = BigDecimal.valueOf(36);
                            KFB = BigDecimal.ZERO;
                        } else {
                            KFB = BigDecimal.ZERO;
                        }
                    }
                }
            }
        }
        ZTABFB = (EFA.add(ANP).add(SAP).add(FVBZ)).setScale(2, RoundingMode.DOWN);
    }

    // Ermittlung Jahreslohnsteuer, PAP Seite 23

    private void MLSTJAHR() {
        UPEVP();
        ZVE = ZRE4.subtract(ZTABFB).subtract(VSP);
        UPMLST();
    }

    // PAP Seite 24

    private void UPLSTLZZ() {
        JW = LSTJAHR.multiply(ZAHL100);
        UPANTEIL();
        LSTLZZ = ANTEIL1;
    }

    // PAP Seite 25

    private void UPMLST() {
        if (ZVE.compareTo(ZAHL1) == -1) {
            ZVE = BigDecimal.ZERO;
            X = BigDecimal.ZERO;
        } else {
            X = (ZVE.divide(BigDecimal.valueOf(KZTAB))).setScale(0, RoundingMode.DOWN);
        }
        if (STKL < 5) {
            UPTAB26();
        } else {
            MST5_6();
        }
    }

    // Vorsorgepauschale (§ 39b Absatz 2 Satz 5 Nummer 3 EStG) PAP Seite 26

    private void UPEVP() {
        if (KRV == 1) {
            VSPR = BigDecimal.ZERO;
        } else {
            if (ZRE4VP.compareTo(BBGRVALV) == 1) {
                ZRE4VPR = BBGRVALV;
            } else {
                ZRE4VPR = ZRE4VP;
            }
            VSPR = (ZRE4VPR.multiply(RVSATZAN)).setScale(2, RoundingMode.DOWN);
        }
        MVSPKVPV();
        if (ALV == 1) {
            // NOP
        } else {
            if (STKL == 6) {
                // NOP
            } else {
                MVSPHB();
            }
        }
    }

    // Vorsorgepauschale (§ 39b Absatz 2 Satz 5 Nummer 3 Buchstaben b bis d EStG), PAP Seite 27

    private void MVSPKVPV() {
        if (ZRE4VP.compareTo(BBGKVPV) == 1) {
            ZRE4VPR = BBGKVPV;
        } else {
            ZRE4VPR = ZRE4VP;
        }
        if (PKV > 0) {
            if (STKL == 6) {
                VSPKVPV = BigDecimal.ZERO;
            } else {
                PKPVAGZJ = PKPVAGZ.multiply(ZAHL12).divide(ZAHL100).setScale(2, RoundingMode.DOWN);
                VSPKVPV = PKPV.multiply(ZAHL12).divide(ZAHL100).setScale(2, RoundingMode.DOWN);
                VSPKVPV = VSPKVPV.subtract(PKPVAGZJ);
                if (VSPKVPV.compareTo(BigDecimal.ZERO) == -1) {
                    VSPKVPV = BigDecimal.ZERO;
                }
            }
        } else {
            VSPKVPV = ZRE4VPR.multiply(KVSATZAN.add(PVSATZAN)).setScale(2, RoundingMode.DOWN);
        }
        VSP = VSPKVPV.add(VSPR).setScale(0, RoundingMode.UP);
    }

    // Höchstbetragsberechnung zur Arbeitslosenversicherung (§ 39b Absatz 2 Satz 5 Nummer 3 Buchstabe e EStG), PAP Seite 28

    private void MVSPHB() {
        if (ZRE4VP.compareTo(BBGRVALV) == 1) {
            ZRE4VPR = BBGRVALV;
        } else {
            ZRE4VPR = ZRE4VP;
        }
        VSPALV = AVSATZAN.multiply(ZRE4VPR).setScale(2, RoundingMode.DOWN);
        VSPHB = VSPALV.add(VSPKVPV).setScale(2, RoundingMode.DOWN);
        if (VSPHB.compareTo(BigDecimal.valueOf(1900)) == 1) {
            VSPHB = BigDecimal.valueOf(1900);
        }
        VSPN = VSPR.add(VSPHB).setScale(0, RoundingMode.UP);
        if (VSPN.compareTo(VSP) == 1) {
            VSP = VSPN;
        }
    }

    // Lohnsteuer fuer die Steuerklassen V und VI (§ 39b Absatz 2 Satz 7 EStG), PAP Seite 29

    private void MST5_6() {
        ZZX = X;
        if (ZZX.compareTo(W2STKL5) == 1) {
            ZX = W2STKL5;
            UP5_6();
            if (ZZX.compareTo(W3STKL5) == 1) {
                ST = (ST.add((W3STKL5.subtract(W2STKL5)).multiply(BigDecimal.valueOf(0.42)))).setScale(0, RoundingMode.DOWN);
                ST = (ST.add((ZZX.subtract(W3STKL5)).multiply(BigDecimal.valueOf(0.45)))).setScale(0, RoundingMode.DOWN);
            } else {
                ST = (ST.add((ZZX.subtract(W2STKL5)).multiply(BigDecimal.valueOf(0.42)))).setScale(0, RoundingMode.DOWN);
            }
        } else {
            ZX = ZZX;
            UP5_6();
            if (ZZX.compareTo(W1STKL5) == 1) {
                VERGL = ST;
                ZX = W1STKL5;
                UP5_6();
                HOCH = (ST.add((ZZX.subtract(W1STKL5)).multiply(BigDecimal.valueOf(0.42)))).setScale(0, RoundingMode.DOWN);
                if (HOCH.compareTo(VERGL) == -1) {
                    ST = HOCH;
                } else {
                    ST = VERGL;
                }
            }
        }
    }

    // Unterprogramm zur Lohnsteuer fuer die Steuerklassen V und VI (§ 39b Absatz 2 Satz 7 EStG), PAP Seite 30

    private void UP5_6() {
        X = (ZX.multiply(BigDecimal.valueOf(1.25))).setScale(0, RoundingMode.DOWN);
        UPTAB26();
        ST1 = ST;
        X = (ZX.multiply(BigDecimal.valueOf(0.75))).setScale(0, RoundingMode.DOWN);
        UPTAB26();
        ST2 = ST;
        DIFF = (ST1.subtract(ST2)).multiply(ZAHL2);
        MIST = (ZX.multiply(BigDecimal.valueOf(0.14))).setScale(0, RoundingMode.DOWN);
        if (MIST.compareTo(DIFF) == 1) {
            ST = MIST;
        } else {
            ST = DIFF;
        }
    }

    // Solidaritätszuschlag, PAP Seite 31

    private void MSOLZ() {
        SOLZFREI = (SOLZFREI.multiply(BigDecimal.valueOf(KZTAB)));
        if (JBMG.compareTo(SOLZFREI) == 1) {
            SOLZJ = (JBMG.multiply(BigDecimal.valueOf(5.5))).divide(ZAHL100).setScale(2, RoundingMode.DOWN);
            SOLZMIN = (JBMG.subtract(SOLZFREI)).multiply(BigDecimal.valueOf(11.9)).divide(ZAHL100).setScale(2, RoundingMode.DOWN);
            if (SOLZMIN.compareTo(SOLZJ) == -1) {
                SOLZJ = SOLZMIN;
            }
            JW = SOLZJ.multiply(ZAHL100).setScale(0, RoundingMode.DOWN);
            UPANTEIL();
            SOLZLZZ = ANTEIL1;
        } else {
            SOLZLZZ = BigDecimal.ZERO;
        }
        if (R > 0) {
            JW = JBMG.multiply(ZAHL100);
            UPANTEIL();
            BK = ANTEIL1;
        } else {
            BK = BigDecimal.ZERO;
        }
    }

    // Anteil von Jahresbeträgen fuer einen LZZ (§ 39b Absatz 2 Satz 9 EStG), PAP Seite 32

    private void UPANTEIL() {
        if (LZZ == 1) {
            ANTEIL1 = JW;
        } else {
            if (LZZ == 2) {
                ANTEIL1 = JW.divide(ZAHL12, 0, RoundingMode.DOWN);
            } else {
                if (LZZ == 3) {
                    ANTEIL1 = (JW.multiply(ZAHL7)).divide(ZAHL360, 0, RoundingMode.DOWN);
                } else {
                    ANTEIL1 = JW.divide(ZAHL360, 0, RoundingMode.DOWN);
                }
            }
        }
    }

    // Berechnung sonstiger Bezüge nach § 39b Absatz 3 Sätze 1 bis 8 EStG, PAP Seite 33

    private void MSONST() {
        LZZ = 1;
        if (ZMVB == 0) {
            ZMVB = 12;
        }
        if (SONSTB.compareTo(BigDecimal.ZERO) == 0 && MBV.compareTo(BigDecimal.ZERO) == 0) {
            LSTSO = BigDecimal.ZERO;
            STS = BigDecimal.ZERO;
            SOLZS = BigDecimal.ZERO;
            BKS = BigDecimal.ZERO;
        } else {
            MOSONST();
            ZRE4J = ((JRE4.add(SONSTB)).divide(ZAHL100)).setScale(2, RoundingMode.DOWN);
            ZVBEZJ = ((JVBEZ.add(VBS)).divide(ZAHL100)).setScale(2, RoundingMode.DOWN);
            VBEZBSO = STERBE;
            MRE4SONST();
            MLSTJAHR();
            WVFRBM = (ZVE.subtract(GFB)).multiply(ZAHL100).setScale(2, RoundingMode.DOWN);
            if (WVFRBM.compareTo(BigDecimal.ZERO) == -1) {
                WVFRBM = BigDecimal.ZERO;
            }
            LSTSO = ST.multiply(ZAHL100);
            // lt. PAP:  "Hinweis: negative Zahlen werden nach ihrem Betrag gerundet!"
            // Fallunterscheidung bzgl. negativer Zahlen nicht nötig, da die Java-Klasse BigDecimal.ROUND_DOWN
            // die Ziffer und nicht die Zahl abrundet, also aus -4.5 wird -4 und aus 4.5 wird 4
            STS = LSTSO.subtract(LSTOSO).multiply(BigDecimal.valueOf(f)).divide(ZAHL100, 0, RoundingMode.DOWN).multiply(ZAHL100);
            STSMIN();
        }
    }

    // PAP Seite 34

    private void STSMIN() {
        if (STS.compareTo(BigDecimal.ZERO) == -1) {
            if (MBV.compareTo(BigDecimal.ZERO) == 0) {
                // NOP
            } else {
                LSTLZZ = LSTLZZ.add(STS);
                if (LSTLZZ.compareTo(BigDecimal.ZERO) == -1) {
                    LSTLZZ = BigDecimal.ZERO;
                }
                SOLZLZZ = SOLZLZZ.add(STS.multiply(BigDecimal.valueOf(5.5).divide(ZAHL100))).setScale(0, RoundingMode.DOWN);
                if (SOLZLZZ.compareTo(BigDecimal.ZERO) == -1) {
                    SOLZLZZ = BigDecimal.ZERO;
                }
                BK = BK.add(STS);
                if (BK.compareTo(BigDecimal.ZERO) == -1) {
                    BK = BigDecimal.ZERO;
                }
            }
            STS = BigDecimal.ZERO;
            SOLZS = BigDecimal.ZERO;
        } else {
            MSOLZSTS();
        }
        if (R > 0) {
            BKS = STS;
        } else {
            BKS = BigDecimal.ZERO;
        }
    }

    // Berechnung des SolZ auf sonstige Bezüge, PAP Seite 35

    private void MSOLZSTS() {
        if (ZKF.compareTo(BigDecimal.ZERO) == 1) {
            SOLZSZVE = ZVE.subtract(KFB);
        } else {
            SOLZSZVE = ZVE;
        }
        if (SOLZSZVE.compareTo(BigDecimal.ONE) == -1) {
            SOLZSZVE = BigDecimal.ZERO;
            X = BigDecimal.ZERO;
        } else {
            X = SOLZSZVE.divide(BigDecimal.valueOf(KZTAB), 0, RoundingMode.DOWN);
        }
        if (STKL < 5) {
            UPTAB26();
        } else {
            MST5_6();
        }
        SOLZSBMG = ST.multiply(BigDecimal.valueOf(f)).setScale(0, RoundingMode.DOWN);
        if (SOLZSBMG.compareTo(SOLZFREI) == 1) {
            SOLZS = STS.multiply(BigDecimal.valueOf(5.5)).divide(ZAHL100, 0, RoundingMode.DOWN);
        } else {
            SOLZS = BigDecimal.ZERO;
        }
    }

    // Sonderberechnung ohne sonstige Bezüge für Berechnung bei sonstigen Bezügen, PAP Seite 36

    private void MOSONST() {
        ZRE4J = (JRE4.divide(ZAHL100)).setScale(2, RoundingMode.DOWN);
        ZVBEZJ = (JVBEZ.divide(ZAHL100)).setScale(2, RoundingMode.DOWN);
        JLFREIB = JFREIB.divide(ZAHL100, 2, RoundingMode.DOWN);
        JLHINZU = JHINZU.divide(ZAHL100, 2, RoundingMode.DOWN);
        MRE4();
        MRE4ABZ();
        ZRE4VP = ZRE4VP.subtract(JRE4ENT.divide(ZAHL100));
        MZTABFB();
        VFRBS1 = ((ANP.add(FVB.add(FVBZ))).multiply(ZAHL100)).setScale(2, RoundingMode.DOWN);
        MLSTJAHR();
        WVFRBO = ((ZVE.subtract(GFB)).multiply(ZAHL100)).setScale(2, RoundingMode.DOWN);
        if (WVFRBO.compareTo(BigDecimal.ZERO) == -1) {
            WVFRBO = BigDecimal.ZERO;
        }
        LSTOSO = ST.multiply(ZAHL100);
    }

    // Sonderberechnung mit sonstigen Bezüge für Berechnung bei sonstigen Bezügen, PAP Seite 37

    private void MRE4SONST() {
        MRE4();
        FVB = FVBSO;
        MRE4ABZ();
        ZRE4VP = ZRE4VP.add(MBV.divide(ZAHL100)).subtract(JRE4ENT.divide(ZAHL100)).subtract(SONSTENT.divide(ZAHL100));
        FVBZ = FVBZSO;
        MZTABFB();
        VFRBS2 = ((((ANP.add(FVB).add(FVBZ))).multiply(ZAHL100))).subtract(VFRBS1);
    }

    // Tarifliche Einkommensteuer §32a EStG, PAP Seite 38

    private void UPTAB26() {
        if (X.compareTo(GFB.add(ZAHL1)) == -1) {
            ST = BigDecimal.ZERO;
        } else {
            if (X.compareTo(BigDecimal.valueOf(17800)) == -1) {
                Y = (X.subtract(GFB)).divide(ZAHL10000, 6, RoundingMode.DOWN);
                RW = Y.multiply(BigDecimal.valueOf(914.51));
                RW = RW.add(BigDecimal.valueOf(1400));
                ST = (RW.multiply(Y)).setScale(0, RoundingMode.DOWN);
            } else {
                if (X.compareTo(BigDecimal.valueOf(69879)) == -1) {
                    Y = (X.subtract(BigDecimal.valueOf(17799))).divide(ZAHL10000, 6, RoundingMode.DOWN);
                    RW = Y.multiply(BigDecimal.valueOf(173.1));
                    RW = RW.add(BigDecimal.valueOf(2397));
                    RW = RW.multiply(Y);
                    ST = (RW.add(BigDecimal.valueOf(1034.87))).setScale(0, RoundingMode.DOWN);
                } else {
                    if (X.compareTo(BigDecimal.valueOf(277826)) == -1) {
                        ST = ((X.multiply(BigDecimal.valueOf(0.42))).subtract(BigDecimal.valueOf(11135.63))).setScale(0, RoundingMode.DOWN);
                    } else {
                        ST = ((X.multiply(BigDecimal.valueOf(0.45))).subtract(BigDecimal.valueOf(19470.38))).setScale(0, RoundingMode.DOWN);
                    }
                }
            }
        }
        ST = ST.multiply(BigDecimal.valueOf(KZTAB));
    }
}
