package com.simulation.airdefense.service;

import com.simulation.airdefense.model.Target;

/**
 * Bir angajman denemesinin sonucu.
 *
 * @param target      denenen hedef
 * @param success     hedef etkisiz hale getirildi mi
 * @param probability kullanılan başarı olasılığı
 * @param x           deneme anındaki hedef konumu x (m)
 * @param y           deneme anındaki hedef konumu y (m)
 */
public record EngagementOutcome(Target target, boolean success, double probability, double x, double y) {
}
