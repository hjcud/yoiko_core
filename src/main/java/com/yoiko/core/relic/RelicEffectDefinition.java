package com.yoiko.core.relic;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record RelicEffectDefinition(
        String key,
        RelicAppraisalCategory category,
        Unit unit,
        Aggregation aggregation,
        double maximumValue,
        String uiFormat,
        RuntimeTarget runtimeTarget,
        boolean cobblemonDependent,
        Application application,
        Set<RelicRarity> allowedRarities
) {
    public RelicEffectDefinition {
        key = key == null ? "" : key;
        category = category == null ? RelicAppraisalCategory.ALL : category;
        unit = unit == null ? Unit.NUMBER : unit;
        aggregation = aggregation == null ? Aggregation.MAXIMUM : aggregation;
        maximumValue = Math.max(0.0D, maximumValue);
        uiFormat = uiFormat == null ? "%.2f" : uiFormat;
        runtimeTarget = runtimeTarget == null ? RuntimeTarget.CORE : runtimeTarget;
        application = application == null ? Application.ADD_FLAT : application;
        allowedRarities = allowedRarities == null || allowedRarities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(allowedRarities));
    }

    public double aggregate(Collection<Double> values) {
        double result = switch (aggregation) {
            case MAXIMUM -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
            case SUM -> values.stream().mapToDouble(Double::doubleValue).sum();
            case MULTIPLY -> values.isEmpty() ? 0.0D
                    : values.stream().mapToDouble(Double::doubleValue).reduce(1.0D, (left, right) -> left * right);
        };
        return Math.max(0.0D, Math.min(maximumValue, result));
    }

    public String format(double value) {
        String rendered = String.format(Locale.ROOT, uiFormat, value);
        return switch (unit) {
            case PERCENT, CHANCE -> rendered + "%";
            case SLOTS -> rendered + " slots";
            case BLOCKS -> rendered + " blocks";
            case SECONDS -> rendered + " seconds";
            case LEVELS -> rendered + " levels";
            case NUMBER -> rendered;
        };
    }

    public double apply(double baseValue, double effectValue) {
        return application.apply(baseValue, Math.max(0.0D, Math.min(maximumValue, effectValue)));
    }

    public enum Unit {
        PERCENT,
        CHANCE,
        SLOTS,
        BLOCKS,
        SECONDS,
        LEVELS,
        NUMBER
    }

    public enum Aggregation {
        MAXIMUM,
        SUM,
        MULTIPLY
    }

    public enum RuntimeTarget {
        CORE,
        STORAGE,
        PLAYER_ATTRIBUTE,
        PLAYER_EVENT,
        COBBLEMON_EVENT,
        COBBLEMON_SPAWN,
        COBBLEMON_RIDING,
        UPGRADE
    }

    public enum Application {
        ADD_FLAT {
            @Override
            double apply(double base, double effect) {
                return base + effect;
            }
        },
        MULTIPLY_PERCENT {
            @Override
            double apply(double base, double effect) {
                return base * (1.0D + effect / 100.0D);
            }
        },
        VALUE {
            @Override
            double apply(double base, double effect) {
                return effect;
            }
        };

        abstract double apply(double base, double effect);
    }
}
