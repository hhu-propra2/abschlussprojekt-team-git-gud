package de.hhu.propra2.material2.mops.database.DTOs;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public final class DateiDTO {
    /**
     * Unique ID from database.
     */

    private long id;
    /**
     * Name of file.
     */

    private String name;

    /**
     * User that uploaded the file.
     */

    private UserDTO uploader;
    /**
     * All assigned tags.
     */

    private List<TagDTO> tagDTOs;
    /**
     * Assigned group.
     */


    private GruppeDTO gruppe;
    /**
     * Upload date.
     */

    private LocalDate uploaddatum;
    /**
     * Date for when the file
     * will be visible to non-uploaders
     * of its group.
     */

    private LocalDate veroeffentlichungsdatum;
    /**
     * File size.
     */

    private long dateigroesse;
    /**
     * File type.
     */

    private String dateityp;
    /**
     * Category for gui.
     */

    private String kategorie;

    /**
     * Standard Constructor for import from database
     * and for saving changes to existing Datei.
     * @param idArg
     * @param nameArg
     * @param uploaderArg
     * @param tagDTOsArg
     * @param uploaddatumArg
     * @param veroeffentlichungsdatumArg
     * @param dateigroesseArg
     * @param dateitypArg
     * @param gruppeArg
     * @param kategorieArg
     */
    public DateiDTO(final long idArg,
                    final String nameArg,
                    final UserDTO uploaderArg,
                    final List<TagDTO> tagDTOsArg,
                    final LocalDate uploaddatumArg,
                    final LocalDate veroeffentlichungsdatumArg,
                    final long dateigroesseArg,
                    final String dateitypArg,
                    final GruppeDTO gruppeArg,
                    final String kategorieArg) {
        this.id = idArg;
        this.name = nameArg;
        this.uploader = uploaderArg;
        this.tagDTOs = tagDTOsArg;
        this.gruppe = gruppeArg;
        this.uploaddatum = uploaddatumArg;
        this.veroeffentlichungsdatum = veroeffentlichungsdatumArg;
        this.dateigroesse = dateigroesseArg;
        this.dateityp = dateitypArg;
        this.kategorie = kategorieArg;
    }

    /**
     * Constructor for saving to database.
     * @param nameArg
     * @param uploaderArg
     * @param tagDTOsArg
     * @param uploaddatumArg
     * @param veroeffentlichungsdatumArg
     * @param dateigroesseArg
     * @param dateitypArg
     * @param gruppeArg
     * @param kategorieArg
     */
    public DateiDTO(final String nameArg,
                    final UserDTO uploaderArg,
                    final List<TagDTO> tagDTOsArg,
                    final LocalDate uploaddatumArg,
                    final LocalDate veroeffentlichungsdatumArg,
                    final long dateigroesseArg,
                    final String dateitypArg,
                    final GruppeDTO gruppeArg,
                    final String kategorieArg) {
        this.id = -1;
        this.name = nameArg;
        this.uploader = uploaderArg;
        this.tagDTOs = tagDTOsArg;
        this.gruppe = gruppeArg;
        this.uploaddatum = uploaddatumArg;
        this.veroeffentlichungsdatum = veroeffentlichungsdatumArg;
        this.dateigroesse = dateigroesseArg;
        this.dateityp = dateitypArg;
        this.kategorie = kategorieArg;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }

        if (o.getClass() == this.getClass()) {
            DateiDTO date = (DateiDTO) o;
            return date.getId() == this.getId();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
