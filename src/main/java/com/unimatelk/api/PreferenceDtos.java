package com.unimatelk.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PreferenceDtos {

    public record PreferenceUpdate(
            @NotNull @Min(1) @Max(5) Integer sleepSchedule,
            @NotNull @Min(1) @Max(5) Integer cleanliness,
            @NotNull @Min(1) @Max(5) Integer noiseTolerance,
            @NotNull @Min(1) @Max(5) Integer guests,
            @NotNull Boolean smokingOk,
            @NotNull Boolean drinkingOk,
            @NotNull @Min(1) @Max(5) Integer introvert
    ) {}
}