package com.example.mealse.ch03;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

@ValidOrderPlanForm
public class OrderPlanForm {

    @NotBlank(message = "プランタイプは必須です")
    @Pattern(regexp = "STANDARD|PREMIUM|CUSTOM",
             message = "プランタイプはSTANDARD、PREMIUM、CUSTOMのいずれかです")
    private String planType;

    @NotNull(message = "配送頻度は必須です")
    @Pattern(regexp = "WEEKLY|BIWEEKLY",
             message = "配送頻度はWEEKLYまたはBIWEEKLYです")
    private String frequency;

    // STANDARD / PREMIUM で使うフィールド
    private String mealSetId;

    // PREMIUM のみで使うフィールド
    private Boolean includeFrozen;

    // CUSTOM のみで使うフィールド
    private List<String> mealIds;
    private LocalDate startDate;

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getMealSetId() { return mealSetId; }
    public void setMealSetId(String mealSetId) { this.mealSetId = mealSetId; }

    public Boolean getIncludeFrozen() { return includeFrozen; }
    public void setIncludeFrozen(Boolean includeFrozen) { this.includeFrozen = includeFrozen; }

    public List<String> getMealIds() { return mealIds; }
    public void setMealIds(List<String> mealIds) { this.mealIds = mealIds; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
}
