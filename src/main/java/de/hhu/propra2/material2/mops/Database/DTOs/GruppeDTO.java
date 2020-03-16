package de.hhu.propra2.material2.mops.Database.DTOs;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
public class GruppeDTO {
    /**
     * Unique ID from database.
     */
    private final long id;
    /**
     * Groups name from database.
     */
    private final String name;
    /**
     * Groups description from database.
     */
    private final String description;
    /**
     * List of related files from database.
     */
    @Getter(AccessLevel.PUBLIC)
    private final List<DateiDTO> dateien;

    /**
     * Standard AllArgsConstructor for import from database.
     *  @param idArg
     * @param nameArg
     * @param descriptionArg
     * @param dateiArgs
     */
    public GruppeDTO(final long idArg,
                     final String nameArg,
                     final String descriptionArg,
                     final List<DateiDTO> dateiArgs) {
        this.id = idArg;
        this.name = nameArg;
        this.description = descriptionArg;
        this.dateien = dateiArgs;
    }
}
