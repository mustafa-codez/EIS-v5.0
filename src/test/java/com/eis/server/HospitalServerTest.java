package com.eis.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HospitalServer#runDDX}, the weighted differential-diagnosis
 * scoring engine. These tests lock in the intended behavior of symptom scoring,
 * age/gender filtering, exclusion rules, and onset modifiers so regressions in the
 * knowledge base are caught automatically.
 */
@DisplayName("HospitalServer DDX engine")
class HospitalServerTest {

    @Test
    @DisplayName("Classic MI presentation returns Myocardial Infarction as top candidate")
    void classicMiPresentationIsTopCandidate() {
        List<String> symptoms = List.of("chest pain", "arm numbness", "rapid heartbeat", "lightheaded");
        List<Map<String, Object>> ddx = HospitalServer.runDDX(symptoms, 58, "male", "SUDDEN");

        assertFalse(ddx.isEmpty(), "Expected at least one matching candidate");
        assertEquals("Myocardial Infarction (STEMI/NSTEMI)", ddx.get(0).get("medicalName"));
        assertEquals("EMERGENCY", ddx.get(0).get("urgency"));
    }

    @Test
    @DisplayName("No symptoms selected returns an empty differential")
    void noSymptomsReturnsEmpty() {
        List<Map<String, Object>> ddx = HospitalServer.runDDX(List.of(), 40, "any", "UNKNOWN");
        assertTrue(ddx.isEmpty());
    }

    @Test
    @DisplayName("Aortic Dissection candidate is excluded for female patients")
    void genderFilterExcludesAorticDissectionForFemales() {
        List<String> symptoms = List.of("chest pain", "severe headache", "arm numbness", "rapid heartbeat", "lightheaded");
        List<Map<String, Object>> ddx = HospitalServer.runDDX(symptoms, 55, "female", "SUDDEN");

        boolean containsAorticDissection = ddx.stream()
                .anyMatch(dx -> "Aortic Dissection".equals(dx.get("medicalName")));
        assertFalse(containsAorticDissection, "Aortic Dissection is male-only and must not appear for female patients");
    }

    @Test
    @DisplayName("Febrile Seizure candidate is excluded outside its paediatric age range")
    void ageFilterExcludesFebrileSeizureForAdults() {
        List<String> symptoms = List.of("twitching", "loss of consciousness");
        List<Map<String, Object>> ddx = HospitalServer.runDDX(symptoms, 45, "any", "SUDDEN");

        boolean containsFebrileSeizure = ddx.stream()
                .anyMatch(dx -> "Febrile Seizure".equals(dx.get("medicalName")));
        assertFalse(containsFebrileSeizure, "Febrile Seizure candidate is capped at age 5");
    }

    @Test
    @DisplayName("At most 3 diagnoses are returned, sorted by descending score")
    void resultsAreCappedAtThreeAndSortedDescending() {
        // A broad symptom set intended to match many candidates at once.
        List<String> symptoms = List.of(
                "chest pain", "arm numbness", "rapid heartbeat", "lightheaded",
                "blurred vision", "severe headache", "loss of consciousness");
        List<Map<String, Object>> ddx = HospitalServer.runDDX(symptoms, 50, "male", "SUDDEN");

        assertTrue(ddx.size() <= 3, "DDX engine must return at most 3 candidates");
        for (int i = 1; i < ddx.size(); i++) {
            int prev = (int) ddx.get(i - 1).get("score");
            int curr = (int) ddx.get(i).get("score");
            assertTrue(prev >= curr, "Results must be sorted by descending score");
        }
    }

    @Test
    @DisplayName("SUDDEN onset never produces a negative score")
    void sudsOnsetNeverProducesNegativeScore() {
        List<String> symptoms = List.of("severe headache", "lightheaded");
        List<Map<String, Object>> ddx = HospitalServer.runDDX(symptoms, 30, "any", "MONTHS");

        for (Map<String, Object> dx : ddx) {
            int score = (int) dx.get("score");
            assertTrue(score >= 0, "Score must never go negative regardless of onset penalty");
        }
    }
}
