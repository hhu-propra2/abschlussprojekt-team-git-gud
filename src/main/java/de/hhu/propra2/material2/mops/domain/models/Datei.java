package de.hhu.propra2.material2.mops.domain.models;

import lombok.Getter;
import lombok.Value;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Value
public class Datei {
    /**
     * Unique ID from database.
     */
    @Getter
    private long id;
    /**
     * Name of file.
     */
    @Getter
    private String name;
    /**
     * User that uploaded the file.
     */
    @Getter
    private final User uploader;
    /**
     * All assigned tags.
     */
    @Getter
    private final List<Tag> tags;
    //final Gruppe gruppe;
    /**
     * Upload date.
     */
    @Getter
    private final LocalDate uploaddatum;
    /**
     * Date for when the file
     * will be visible to non-uploaders
     * of its group.
     */

    @Getter
    private final LocalDate veroeffentlichungsdatum;
    /**
     * File size.
     */
    @Getter
    private final long dateigroesse;
    /**
     * File type.
     */
    @Getter
    private final String dateityp;

    private final String kategorie;

    private List<String> getTagNames() {
        return tags.stream()
                .map(Tag::getText)
                .collect(Collectors.toList());
    }

    /**
     * text of the tags of the file as an array.
     *
     * @return text of the tags of the file as an array
     */
    public String[] getTagNamesAsArray() {
        List<String> tagNames = getTagNames();
        String[] abs = new String[tagNames.size()];
        return tagNames.toArray(abs);
    }

    private boolean hatTag(final String tag) {
        for (String tempTag : this.getTagNames()) {
            if (tempTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param tagsToCheckFor Array of Strings
     * @return true if the file contains all the given tags
     */
    public boolean hatTags(final String[] tagsToCheckFor) {
        for (String tag : tagsToCheckFor) {
            if (!this.hatTag(tag)) {
                return false;
            }
        }
        return true;
    }

    public Datei(
            final long idArgs,
            final String nameArgs,
            final User uploaderArgs,
            final List<Tag> tagsArgs,
            final LocalDate uploaddatumArgs,
            final LocalDate veroeffentlichungsdatumArgs,
            final long dateigroesseArgs,
            final String dateitypArgs,
            final String kategorieArgs) {
        this.id = idArgs;
        this.name = nameArgs;
        this.uploader = uploaderArgs;
        this.tags = tagsArgs;
        this.uploaddatum = uploaddatumArgs;
        this.veroeffentlichungsdatum = veroeffentlichungsdatumArgs;
        this.dateigroesse = dateigroesseArgs;
        this.dateityp = dateitypArgs;
        this.kategorie = kategorieArgs;
    }

    /**
     * gives out the FileSize formet as a String with MB/GB etc.
     * Quelle: https://www.javaguides.net/2018/07/how-to-get-file-size-in-bytes-kb-mb-gb.html
     *
     * @return String of filesize
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public String getDateigroesseFormated() {
        if (dateigroesse <= 0) {
            return "0";
        }
        final String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(dateigroesse) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(dateigroesse / Math.pow(1024, digitGroups))
                + " " + units[digitGroups];
    }
}
