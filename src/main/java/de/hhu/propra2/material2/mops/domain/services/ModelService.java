package de.hhu.propra2.material2.mops.domain.services;

import de.hhu.propra2.material2.mops.Exceptions.FileNotPublishedYetException;
import de.hhu.propra2.material2.mops.Exceptions.NoAccessPermissionException;
import de.hhu.propra2.material2.mops.Exceptions.NoDownloadPermissionException;
import de.hhu.propra2.material2.mops.database.DTOs.DateiDTO;
import de.hhu.propra2.material2.mops.database.DTOs.GruppeDTO;
import de.hhu.propra2.material2.mops.database.DTOs.TagDTO;
import de.hhu.propra2.material2.mops.database.DTOs.UserDTO;
import de.hhu.propra2.material2.mops.database.Repository;
import de.hhu.propra2.material2.mops.domain.models.Datei;
import de.hhu.propra2.material2.mops.domain.models.Gruppe;
import de.hhu.propra2.material2.mops.domain.models.Suche;
import de.hhu.propra2.material2.mops.domain.models.Tag;
import de.hhu.propra2.material2.mops.domain.models.User;
import de.hhu.propra2.material2.mops.security.Account;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public final class ModelService implements IModelService {


    private final Repository repository;
    private final SuchService suchService;
    private Suche suche;

    /**
     * Constructor of ModelService.
     */
    public ModelService(final Repository repositoryArg, final SuchService suchServiceArg) {
        repository = repositoryArg;
        suchService = suchServiceArg;
    }

    private Datei loadDatei(final DateiDTO dateiDTO) {
        List<Tag> tags = dateiDTO.getTagDTOs().stream()
                .map(this::loadTag)
                .collect(Collectors.toList());
        return new Datei(
                dateiDTO.getId(),
                dateiDTO.getName(),
                loadUploader(dateiDTO.getUploader()),
                tags,
                dateiDTO.getUploaddatum(),
                dateiDTO.getVeroeffentlichungsdatum(),
                dateiDTO.getDateigroesse(),
                dateiDTO.getDateityp(),
                dateiDTO.getKategorie());
    }

    private Tag loadTag(final TagDTO tagDTO) {
        return new Tag(tagDTO.getId(), tagDTO.getText());
    }

    private User loadUser(final UserDTO userDTO) {

        if (userDTO == null) {
            return new User(-1L, "", "", "", new HashMap<>());
        }

        return new User(
                userDTO.getId(),
                userDTO.getVorname(),
                userDTO.getNachname(),
                userDTO.getKeycloakname(),
                convertHashMapGruppeDTOtoGruppe(userDTO));
    }

    public HashMap<Gruppe, Boolean> convertHashMapGruppeDTOtoGruppe(final UserDTO userDTO) {
        HashMap<Gruppe, Boolean> belegungUndRechte = new HashMap<>();
        for (GruppeDTO gruppeDTO : userDTO.getBelegungUndRechte().keySet()) {
            belegungUndRechte.put(
                    loadGruppe(gruppeDTO),
                    userDTO.getBelegungUndRechte().get(gruppeDTO));
        }
        return belegungUndRechte;
    }

    private Gruppe loadGruppe(final GruppeDTO gruppeDTO) {
        return new Gruppe(gruppeDTO.getId(), gruppeDTO.getName(), dateienDerGruppe(gruppeDTO));
    }

    private User loadUploader(final UserDTO dto) {
        return new User(
                dto.getId(),
                dto.getVorname(),
                dto.getNachname(),
                dto.getKeycloakname(),
                new HashMap<>());
    }

    public List<Datei> dateienDerGruppe(final GruppeDTO gruppeDTO) {
        return gruppeDTO.getDateien()
                .stream()
                .map(this::loadDatei)
                .collect(Collectors.toList());

    }

    public List<Gruppe> getAlleGruppenByUser(final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        return user.getAllGruppen();
    }

    public List<Gruppe> getAlleUploadGruppenByUser(final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        return user.getAllGruppenWithUploadrechten();
    }

    public Gruppe getGruppeByUserAndGroupID(final String gruppeId,
                                            final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        return user.getGruppeById(gruppeId);
    }

    public List<Datei> getAlleDateienByGruppe(final String gruppeId,
                                              final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        Gruppe gruppe = user.getGruppeById(gruppeId);
        if (user.getBelegungUndRechte().get(gruppe)) {
            return gruppe.getDateien();
        }
        return filterVeroeffentlichung(gruppe.getDateien());
    }

    public Set<String> getAlleTagsByUser(final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        List<Gruppe> groups = user.getAllGruppen();
        Set<String> tags = new HashSet<>();
        for (Gruppe gruppe : groups) {
            gruppe.getDateien().forEach(datei -> datei.getTags()
                    .forEach(tag -> tags.add(tag.getText())));
        }
        return tags;
    }

    public Set<String> getAlleTagsByGruppe(final String gruppeId,
                                           final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        List<Datei> dateienListe = user.getGruppeById(gruppeId).getDateien();
        Set<String> tags = new HashSet<>();
        dateienListe.forEach(datei -> datei.getTags()
                .forEach(tag -> tags.add(tag.getText())));
        return tags;
    }

    public Set<String> getAlleUploaderByUser(final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        List<Gruppe> groups = user.getAllGruppen();
        Set<String> uploader = new HashSet<>();
        for (Gruppe gruppe : groups) {
            uploader.addAll(gruppe.getDateien()
                    .stream()
                    .map(datei -> datei.getUploader().getNachname())
                    .collect(Collectors.toSet()));
        }
        return uploader;
    }

    public Set<String> getAlleUploaderByGruppe(final String gruppeId,
                                               final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        return user.getGruppeById(gruppeId).getDateien()
                .stream()
                .map(datei -> datei.getUploader().getNachname())
                .collect(Collectors.toSet());
    }

    public void suchen(final Suche sucheArg) {
        this.suche = sucheArg;
    }

    public List<Datei> getSuchergebnisse(final KeycloakAuthenticationToken token) {
        List<Datei> zuFiltern;
        if ("-1".equals(suche.getGruppenId())) {
            User user = createUserByToken(token);
            zuFiltern = getAlleDateienByUser(user);
        } else {
            zuFiltern = getAlleDateienByGruppe(suche.getGruppenId(), token);
        }
        return suchService.starteSuche(suche, zuFiltern);
    }

    public Set<String> getKategorienFromSuche(final List<Datei> dateien) {
        Set<String> kategorien = new HashSet<>();
        if (dateien == null) {
            return kategorien;
        }
        dateien.forEach(datei -> kategorien.add(datei.getKategorie()));
        return kategorien;
    }

    public Set<String> getKategorienByGruppe(final String gruppeId, final KeycloakAuthenticationToken token) {
        List<Datei> dateien = getAlleDateienByGruppe(gruppeId, token);
        Set<String> kategorien = new HashSet<>();
        dateien.forEach(datei -> kategorien.add(datei.getKategorie()));
        return kategorien;
    }

    public Boolean isSortedByKategorie() {
        return "Kategorie".equals(suche.getSortierKriterium());
    }

    public Set<String> getAlleDateiTypenByUser(final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        List<Gruppe> groups = user.getAllGruppen();
        Set<String> dateiTypen = new HashSet<>();
        for (Gruppe gruppe : groups) {
            dateiTypen.addAll(gruppe.getDateien()
                    .stream()
                    .map(Datei::getDateityp)
                    .collect(Collectors.toSet()));
        }
        return dateiTypen;
    }

    public Set<String> getAlleDateiTypenByGruppe(final String gruppeId,
                                                 final KeycloakAuthenticationToken token) {
        User user = createUserByToken(token);
        return user.getGruppeById(gruppeId).getDateien()
                .stream()
                .map(Datei::getDateityp)
                .collect(Collectors.toSet());
    }

    public Account getAccountFromKeycloak(final KeycloakAuthenticationToken token) {
        KeycloakPrincipal principal = (KeycloakPrincipal) token.getPrincipal();
        return new Account(
                principal.getName(),
                principal.getKeycloakSecurityContext().getIdToken().getEmail(),
                null,
                token.getAccount().getRoles());
    }

    private User createUserByToken(final KeycloakAuthenticationToken token) {
        Account account = getAccountFromKeycloak(token);

        try {
            return loadUser(repository.findUserByKeycloakname(account.getName()));
        } catch (SQLException e) {
            e.printStackTrace();
            return new User(-1L, "", "", "", new HashMap<>());
        }
    }

    /**
     * get datei by dateiId and UserToken.
     *
     * @param dateiId Id of the file
     * @param token   KeycloakAuthenticationToken of the user
     * @return Datei if file is found in the given group of the given user, null if not
     */
    public Datei getDateiById(final long dateiId,
                              final KeycloakAuthenticationToken token) throws NoAccessPermissionException {
        User user = createUserByToken(token);
        List<Gruppe> gruppen = user.getAllGruppen();
        for (Gruppe gruppe : gruppen) {
            for (Datei datei : gruppe.getDateien()) {
                if (datei.getId() == dateiId) {
                    return datei;
                }
            }
        }

        throw new NoAccessPermissionException();
    }

    private List<Datei> getAlleDateienByUser(final User user) {
        List<Datei> alleDateien = new ArrayList<>();
        user.getAllGruppen().forEach(gruppe -> alleDateien.addAll(gruppe.getDateien()));
        for (Gruppe gruppe : user.getAllGruppen()) {
            List<Datei> dateienGruppe = gruppe.getDateien();
            if (user.getBelegungUndRechte().get(gruppe)) {
                alleDateien.addAll(dateienGruppe);
            } else {
                alleDateien.addAll(filterVeroeffentlichung(dateienGruppe));
            }
        }
        return alleDateien;
    }

    public long saveDatei(final Datei datei, final Gruppe gruppe) throws SQLException {
        if (datei == null || gruppe == null) {
            throw new IllegalArgumentException();
        }
        // create GruppeDTO and UserDTO only with Id as parameter because Id is the only parameter which is necessary
        // for saving the file
        GruppeDTO groupDTO = new GruppeDTO(gruppe.getId(), null,
                null, null);
        return saveDatei(datei, groupDTO);
    }

    public void saveDatei(final Datei datei, final String gruppenId) throws SQLException {
        if (datei == null) {
            throw new IllegalArgumentException();
        }
        // create GruppeDTO and UserDTO only with Id as parameter because Id is the only parameter which is necessary
        // for saving the file
        GruppeDTO groupDTO = new GruppeDTO(gruppenId, null,
                null, null);
        saveDatei(datei, groupDTO);
    }

    private long saveDatei(final Datei datei, final GruppeDTO gruppeDTO) throws SQLException {
        UserDTO userDTO = new UserDTO(datei.getUploader().getId(), null, null,
                null, null);

        DateiDTO dateiDTO = new DateiDTO(datei.getId(), datei.getName(), userDTO, tagsToTagDTOs(datei.getTags()),
                datei.getUploaddatum(), datei.getVeroeffentlichungsdatum(), datei.getDateigroesse(),
                datei.getDateityp(), gruppeDTO, datei.getKategorie());
        return repository.saveDatei(dateiDTO);
    }

    ArrayList<TagDTO> tagsToTagDTOs(final List<Tag> tags) {
        ArrayList<TagDTO> tagDTOs = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return tagDTOs;
        }
        for (Tag tag : tags) {
            tagDTOs.add(new TagDTO(tag.getText()));
        }
        return tagDTOs;
    }

    public User findUserByKeycloakname(final String keycloakname) throws SQLException {
        return loadUser(repository.findUserByKeycloakname(keycloakname));
    }
    public Set<String> getTagsAsSet(final String[] tags) {
        if (tags == null) {
            return null;
        }
        return Set.of(tags);
    }
    private List<Datei> filterVeroeffentlichung(final List<Datei> resultArg) {
        LocalDate today = LocalDate.now();
        List<Datei> result = resultArg.stream()
                .filter(datei -> datei.getVeroeffentlichungsdatum().compareTo(today) <= 0)
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public Boolean userHasEditPermissionForFile(final Long dateiId, final KeycloakAuthenticationToken token)
            throws NoDownloadPermissionException, SQLException {
        Account account = getAccountFromKeycloak(token);
        DateiDTO dateiDTO = repository.findDateiById(dateiId);
        User user = findUserByKeycloakname(account.getName());
        String gruppenId = dateiDTO.getGruppe().getId();
        Gruppe gruppe = user.getGruppeById(gruppenId);

        if (!user.hasUploadPermission(gruppe)) {
            throw new NoDownloadPermissionException("User has no download permission");
        }
        return true;
    }

    @Override
    public Boolean filesIsPublished(final Long fileId) throws SQLException, FileNotPublishedYetException {
        DateiDTO dateiDTO = repository.findDateiById(fileId);
        LocalDate veroeffentlichung = dateiDTO.getVeroeffentlichungsdatum();
        LocalDate now = LocalDate.now();
        if (veroeffentlichung.isAfter(now)) {
            throw new FileNotPublishedYetException("Die Datei ist noch nicht veröffentlicht.");
        }
        return true;
    }
}
