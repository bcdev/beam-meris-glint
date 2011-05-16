package org.esa.beam.atmosphere.operator;

/**
 * Used for specifying the output of reflectances.
 * The output can be either radiance reflectances or irradiance reflectances.
 *
 * @author Marco
 * @since since 1.5.1
 */
public enum ReflectanceEnum {
    RADIANCE_REFLECTANCES,
    IRRADIANCE_REFLECTANCES {
        @Override
        public String toString() {
            return super.toString() + " (ESA compatible)";
        }
    }
}
