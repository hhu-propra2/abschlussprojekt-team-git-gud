package de.hhu.propra2.material2.mops.domain.models;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadForm {
    private String gruppenId;
    private String dateiname;
    private String kategorie;
    private String selectedTags;
    private String timedUpload;
    private MultipartFile datei;
}
