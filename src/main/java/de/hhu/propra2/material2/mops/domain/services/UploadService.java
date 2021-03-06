package de.hhu.propra2.material2.mops.domain.services;

import com.google.common.base.Strings;
import de.hhu.propra2.material2.mops.Exceptions.HasNoGroupToUploadException;
import de.hhu.propra2.material2.mops.Exceptions.NoUploadPermissionException;
import de.hhu.propra2.material2.mops.domain.models.Datei;
import de.hhu.propra2.material2.mops.domain.models.Gruppe;
import de.hhu.propra2.material2.mops.domain.models.Tag;
import de.hhu.propra2.material2.mops.domain.models.UploadForm;
import de.hhu.propra2.material2.mops.domain.models.User;
import org.apache.commons.io.FilenameUtils;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UploadService implements IUploadService {

    private final ModelService modelService;
    private final MinIOService minIOService;

    public UploadService(final ModelService modelServiceArg,
                         final MinIOService minIOServiceArg) {
        this.modelService = modelServiceArg;
        this.minIOService = minIOServiceArg;
    }

    private Datei dateiHochladen(final MultipartFile file, final String newFileName,
                                 final User user,
                                 final Gruppe gruppe,
                                 final LocalDate veroeffentlichungsdatum,
                                 final List<Tag> tags,
                                 final String kategorie) throws FileUploadException, SQLException {
        String fileName = Strings.isNullOrEmpty(newFileName) ? file.getOriginalFilename() : newFileName;
        String fileExtension = FilenameUtils.getExtension(fileName);
        //if the newFileName does not have an extension use the original file extension
        if (Strings.isNullOrEmpty(fileExtension)) {
            fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
            fileName += "." + fileExtension;
        }
        Datei datei = new Datei(-1, fileName, user, tags,
                LocalDate.now(), veroeffentlichungsdatum, file.getSize(), fileExtension, kategorie);
        long dateiId = modelService.saveDatei(datei, gruppe);

        if (!minIOService.upload(file, String.valueOf(dateiId))) {
            throw new FileUploadException("Could not save file in data storage");
        }
        return new Datei(dateiId, datei.getName(), datei.getUploader(), datei.getTags(),
                datei.getUploaddatum(), datei.getVeroeffentlichungsdatum(), datei.getDateigroesse(),
                datei.getDateityp(), datei.getKategorie());
    }

    /**
     * starts upload.
     */
    @Override
    @Transactional
    public void startUpload(final UploadForm upForm, final String uploader) throws NoUploadPermissionException,
            SQLException, FileUploadException, HasNoGroupToUploadException {
        User user = modelService.findUserByKeycloakname(uploader);
        if (upForm.getGruppenId() == null) {
            throw new HasNoGroupToUploadException("User has no Group selected to which he can upload");
        }
        Gruppe gruppe = user.getGruppeById(upForm.getGruppenId());
        if (!user.hasUploadPermission(gruppe)) {
            throw new NoUploadPermissionException("User has no upload permission");
        }

        if (Strings.isNullOrEmpty(upForm.getTimedUpload())) {
            upForm.setTimedUpload(LocalDate.now().toString());
        }

        dateiHochladen(upForm.getDatei(), upForm.getDateiname(), user, gruppe,
                parseStringToDate(upForm.getTimedUpload()), convertSeperatedStringToList(upForm.getSelectedTags()),
                upForm.getKategorie());
    }

    private ArrayList<Tag> convertSeperatedStringToList(final String tagStrings) {
        if (Strings.isNullOrEmpty(tagStrings)) {
            return new ArrayList<>();
        }
        ArrayList<String> tagTexts = Arrays.stream(tagStrings.split(","))
                .map(String::trim)
                .map(String::toLowerCase).collect(Collectors.toCollection(ArrayList::new));

        ArrayList<Tag> tags = new ArrayList<>();
        for (String tagText : tagTexts) {
            tagText = tagText.trim();
            if (tagText.isEmpty()) {
                continue;
            }
            tags.add(new Tag(1, tagText));
        }
        return tags;
    }

    private LocalDate parseStringToDate(final String dateString) {
        return Strings.isNullOrEmpty(dateString) ? null : LocalDate.parse(dateString);
    }
}
