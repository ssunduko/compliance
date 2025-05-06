package com.salesmsg.compliance.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for holding results from AWS Textract analysis.
 * Contains extracted text and form elements from images.
 */
public class TextractAnalysisResultDTO {
    private final String text;
    private final String formElementsDescription;
    private final Map<String, List<String>> formFields;
    private final Map<String, String> selectionElements;

    public TextractAnalysisResultDTO(
            String text,
            String formElementsDescription,
            Map<String, List<String>> formFields,
            Map<String, String> selectionElements) {
        this.text = text;
        this.formElementsDescription = formElementsDescription;
        this.formFields = formFields;
        this.selectionElements = selectionElements;
    }

    public String getText() {
        return text;
    }

    public String getFormElementsDescription() {
        return formElementsDescription;
    }

    public Map<String, List<String>> getFormFields() {
        return formFields;
    }

    public Map<String, String> getSelectionElements() {
        return selectionElements;
    }
}