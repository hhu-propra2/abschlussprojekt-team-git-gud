package de.hhu.propra2.material2.mops.database;

import de.hhu.propra2.material2.mops.database.DTOs.DateiDTO;
import de.hhu.propra2.material2.mops.database.DTOs.GruppeDTO;
import de.hhu.propra2.material2.mops.database.DTOs.StatusDTO;
import de.hhu.propra2.material2.mops.database.DTOs.TagDTO;
import de.hhu.propra2.material2.mops.database.DTOs.UserDTO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

//https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html
//it seems to work fine not sure how to fix this
@SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE")
@Component
public final class Repository {
    private Connection connection;
    @Getter(AccessLevel.PACKAGE) //for Testing
    private HashMap<String, GruppeDTO> gruppeCache;

    /**
     * Constructor that autowires
     * an Environment to load up
     * the connection to the database
     * which needs to be done only once.
     *
     * @param envArg
     */
    @Autowired
    public Repository(final Environment envArg) {
        gruppeCache = new HashMap<>();
        try {
            connection = DriverManager.getConnection(envArg.getProperty("spring.datasource.url"),
                    envArg.getProperty("spring.datasource.username"), envArg.getProperty("spring.datasource.password"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
        PUBLIC METHODS
     */

    /**
     * Lazy (no File Loading) search for a user that loads
     * all his groups, rights but no
     * files assigned to their group with
     * their tags.
     * Returns null if no user found.
     *
     * @param keyCloakNameArg
     * @return
     * @throws SQLException
     */
    public UserDTO findUserByKeycloakname(final String keyCloakNameArg) throws SQLException {
        UserDTO user = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from User where key_cloak_name=?");
        preparedStatement.setString(1, keyCloakNameArg);

        ResultSet users = preparedStatement.executeQuery();

        if (users.next()) {
            user = new UserDTO(users.getLong("userID"),
                    users.getString("vorname"),
                    users.getString("nachname"),
                    users.getString("key_cloak_name"),
                    findAllGruppeByUserID(users.getLong("userID")));
        }

        preparedStatement.close();
        users.close();

        return user;
    }

    /**
     * Saves a User with all his groups
     * and rights.
     * Resets and reassigns all User - Group Relations.
     * <p>
     * To be used for syncronization
     * with gruppenbildung.
     *
     * @param userDTO
     * @throws SQLException
     */
    @SuppressWarnings("checkstyle:magicnumber")
    @SuppressFBWarnings("WMI_WRONG_MAP_ITERATOR")
    //need to iterate through both value and keys.
    public void saveUser(final UserDTO userDTO) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "insert ignore into User (userID, vorname, nachname, key_cloak_name) values (?, ?, ?, ?)");

        preparedStatement.setLong(1, userDTO.getId());
        preparedStatement.setString(2, userDTO.getVorname());
        preparedStatement.setString(3, userDTO.getNachname());
        preparedStatement.setString(4, userDTO.getKeycloakname());
        preparedStatement.execute();

        HashMap<GruppeDTO, Boolean> gruppenBelegung = userDTO.getBelegungUndRechte();

        for (GruppeDTO gruppe : gruppenBelegung.keySet()) {
            saveGruppe(gruppe);
            deleteUserGroupRelationByGroupId(gruppe.getId());
            saveGruppenbelegung(userDTO.getId(), gruppe.getId(), gruppenBelegung.get(gruppe));
        }
        preparedStatement.close();
    }

    /**
     * Deletes User by UserDTO.
     * To be used for synchronization
     * with Gruppenbildung. Also removes all its
     * Group - User relations before deleting the user.
     * Extra step: changes all its uploaded files uploaderID
     * to the userID of the placeholder for deletedUsers.
     *
     * @param userDTO
     * @throws SQLException
     */
    public void deleteUserByUserDTO(final UserDTO userDTO) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from User where userID=?");
        preparedStatement.setLong(1, userDTO.getId());

        deleteUserGroupRelationByUserId(userDTO.getId());
        changeUploaderToDeletedForAllDateiByUploaderId(userDTO.getId());

        preparedStatement.execute();
        preparedStatement.close();
    }

    /**
     * Removes Group - User Relation by UserDTO and GruppeDTO.
     * Nothing special just another statement call.
     *
     * @param userDTO
     * @param gruppeDTO
     * @throws SQLException
     */
    public void deleteUserGroupRelationByUserDTOAndGruppeDTO(final UserDTO userDTO,
                                                             final GruppeDTO gruppeDTO) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Gruppenbelegung where gruppeID=? and userID=?");
        preparedStatement.setString(1, gruppeDTO.getId());
        preparedStatement.setLong(2, userDTO.getId());

        preparedStatement.execute();
        preparedStatement.close();
    }

    /**
     * Deletes a group by its ID.
     * To be used for synchronization
     * with grupppenbildung.
     *
     * @param gruppeDTO
     * @throws SQLException
     */
    public void deleteGroupByGroupDTO(final GruppeDTO gruppeDTO) throws SQLException {
        deleteUserGroupRelationByGroupId(gruppeDTO.getId());

        for (DateiDTO dateiDTO : gruppeDTO.getDateien()) {
            deleteDateiByDateiDTO(dateiDTO);
        }

        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Gruppe where gruppeID=?");
        preparedStatement.setString(1, gruppeDTO.getId());

        preparedStatement.execute();
        preparedStatement.close();
    }

    /**
     * Saves or updates a file with all its tags
     * by dateiDTO and
     * returns its generated ID.
     * <p>
     * First it checks if the file already exists in
     * the database. If so it calls the update function which only
     * updates the existing files meta data.
     * If it does not exist it saves the File with its
     * tags, which also updates tag - datei relations.
     * <p>
     * Removes its group from the cache either way.
     *
     * @param dateiDTO
     * @throws SQLException
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public long saveDatei(final DateiDTO dateiDTO) throws SQLException {

        if (!dateiExists(dateiDTO)) {
            PreparedStatement preparedStatement =
                    connection.prepareStatement(
                            "insert into Datei (name, uploaderID, upload_datum,"
                                    + "veroeffentlichungs_datum, datei_groesse,"
                                    + "datei_typ, gruppeID, kategorie) "
                                    + " values (?, ?, ?, ?, ?, ? ,?, ?)", Statement.RETURN_GENERATED_KEYS);

            preparedStatement.setString(1, dateiDTO.getName());
            preparedStatement.setLong(2, dateiDTO.getUploader().getId());
            preparedStatement.setDate(3, java.sql.Date.valueOf(dateiDTO.getUploaddatum()),
                    java.util.Calendar.getInstance());
            preparedStatement.setDate(4, java.sql.Date.valueOf(dateiDTO.getVeroeffentlichungsdatum()),
                    java.util.Calendar.getInstance());
            preparedStatement.setLong(5, dateiDTO.getDateigroesse());
            preparedStatement.setString(6, dateiDTO.getDateityp());
            preparedStatement.setString(7, dateiDTO.getGruppe().getId());
            preparedStatement.setString(8, dateiDTO.getKategorie());

            List<TagDTO> tags = dateiDTO.getTagDTOs();
            preparedStatement.execute();

            ResultSet id = preparedStatement.getGeneratedKeys();
            id.next();
            long dateiId = id.getLong(1);

            for (TagDTO tag : tags) {
                saveTag(tag, dateiId);
            }
            preparedStatement.close();
            id.close();

            if (gruppeCache.get(dateiDTO.getGruppe().getId()) != null) {
                gruppeCache.get(dateiDTO.getGruppe().getId()).setDateien(new LinkedList<DateiDTO>());
                gruppeCache.remove(dateiDTO.getGruppe().getId());
            }

            return dateiId;
        } else {
            updateDatei(dateiDTO, dateiDTO.getId());
            if (gruppeCache.get(dateiDTO.getGruppe().getId()) != null) {
                gruppeCache.get(dateiDTO.getGruppe().getId()).setDateien(new LinkedList<DateiDTO>());
                gruppeCache.remove(dateiDTO.getGruppe().getId());
            }
            return dateiDTO.getId();
        }
    }

    /**
     * Searches for DateiDTO by its ID. Nothing special. Just an SQL query.
     * Returns null if no file found.
     *
     * @param id
     * @return
     * @throws SQLException
     */
    public DateiDTO findDateiById(final long id) throws SQLException {
        DateiDTO datei = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Datei where dateiID=?");
        preparedStatement.setString(1, "" + id);

        ResultSet dateiResult = preparedStatement.executeQuery();

        if (dateiResult.next()) {
            datei = new DateiDTO(dateiResult.getLong("dateiID"),
                    dateiResult.getString("name"),
                    findUserByIdLAZY(dateiResult.getLong("uploaderID")),
                    findAllTagsbyDateiId(id),
                    dateiResult.getDate("upload_datum").toLocalDate(),
                    dateiResult.getDate("veroeffentlichungs_datum").toLocalDate(),
                    dateiResult.getLong("datei_groesse"),
                    dateiResult.getString("datei_typ"),
                    findGruppeByGruppeId(dateiResult.getString("gruppeID")),
                    dateiResult.getString("kategorie"));
        }

        preparedStatement.close();
        dateiResult.close();

        return datei;
    }

    /**
     * Returns a LinkedList of DateiDTO by GruppeDTO.
     * Checks the cache first for the GruppeDTO and
     * proceeds with looking for the DateiDTOs if
     * no valid entry can be found in the cache.
     * <p>
     * Returns empty LinkedList if no files found.
     *
     * @param gruppeDTO
     * @return
     * @throws SQLException
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public LinkedList<DateiDTO> findAllDateiByGruppeDTO(final GruppeDTO gruppeDTO) throws SQLException {
        GruppeDTO cachedGruppe = gruppeCache.get(gruppeDTO.getId());

        if (cachedGruppe != null) {
            if (!(cachedGruppe.hasNoFiles())) {
                return (LinkedList<DateiDTO>) cachedGruppe.getDateien();
            }
        } else {
            gruppeCache.put(gruppeDTO.getId(), gruppeDTO);
        }

        LinkedList<DateiDTO> dateien = new LinkedList<>();

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Datei where gruppeID=?");
        preparedStatement.setString(1, "" + gruppeDTO.getId());

        ResultSet dateiResult = preparedStatement.executeQuery();
        while (dateiResult.next()) {
            dateien.add(findDateiById(dateiResult.getLong("dateiID")));
        }

        preparedStatement.close();
        dateiResult.close();

        return dateien;
    }

    /**
     * Deletes Datei by its DTO.
     * Removes it's tag relations on the way and
     * removes its own group from the cache.
     *
     * @param dateiDTO
     * @throws SQLException
     */
    public void deleteDateiByDateiDTO(final DateiDTO dateiDTO) throws SQLException {
        deleteTagRelationsByDateiId(dateiDTO.getId());

        String gruppeId = dateiDTO.getGruppe().getId();

        LinkedList<DateiDTO> dateien = (LinkedList<DateiDTO>) dateiDTO.getGruppe().getDateien();
        dateien.remove(dateiDTO);
        gruppeCache.remove(gruppeId);


        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Datei where dateiID=?");
        preparedStatement.setLong(1, dateiDTO.getId());

        preparedStatement.execute();

        preparedStatement.close();
    }

    /*
        --- PACKAGE PRIVATE METHODS ---

        DATEI METHODS
     */

    private LinkedList<DateiDTO> findAllDateiByUploaderId(final long userId) throws SQLException {
        LinkedList<DateiDTO> dateien = new LinkedList<>();

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Datei where uploaderID=?");

        preparedStatement.setString(1, "" + userId);

        ResultSet dateiResult = preparedStatement.executeQuery();
        while (dateiResult.next()) {
            dateien.add(findDateiById(dateiResult.getLong("dateiID")));
        }

        preparedStatement.close();
        dateiResult.close();

        return dateien;
    }

    private void changeUploaderToDeletedForAllDateiByUploaderId(final long userId) throws SQLException {
        LinkedList<DateiDTO> dateien = findAllDateiByUploaderId(userId);

        for (DateiDTO dateiDTO : dateien) {
            dateiDTO.setUploader(new UserDTO(-1, "User", "Deleted", "-", null));
            updateDatei(dateiDTO, dateiDTO.getId());
        }

    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void updateDatei(final DateiDTO dateiDTO, final long dateiId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "update Datei set uploaderID=?, veroeffentlichungs_datum=?, datei_groesse=?, kategorie=?,"
                                + "name=? where dateiID=?");

        preparedStatement.setLong(1, dateiDTO.getUploader().getId());
        preparedStatement.setDate(2, java.sql.Date.valueOf(dateiDTO.getVeroeffentlichungsdatum()),
                java.util.Calendar.getInstance());
        preparedStatement.setLong(3, dateiDTO.getDateigroesse());
        preparedStatement.setString(4, dateiDTO.getKategorie());
        preparedStatement.setString(5, dateiDTO.getName());
        preparedStatement.setLong(6, dateiId);
        List<TagDTO> tags = dateiDTO.getTagDTOs();
        preparedStatement.execute();

        deleteTagRelationsByDateiId(dateiDTO.getId());

        for (TagDTO tag : tags) {
            saveTag(tag, dateiDTO.getId());
        }
        preparedStatement.close();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    boolean dateiExists(final DateiDTO dateiDTO) throws SQLException {
        boolean doesItExist;

        if (dateiDTO.getId() == -1) {
            return false;
        }

        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "select * from Datei where dateiID=?");

        preparedStatement.setString(1, "" + dateiDTO.getId());

        ResultSet result = preparedStatement.executeQuery();

        doesItExist = result.next();

        preparedStatement.close();
        result.close();

        return doesItExist;
    }

    /*
        TAG METHODS
     */

    private void saveTagnutzung(final long dateiId, final long tagId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "insert ignore into Tagnutzung (dateiID, tagID)" + " values (?, ?)");

        preparedStatement.setLong(1, dateiId);
        preparedStatement.setLong(2, tagId);
        preparedStatement.execute();

        preparedStatement.close();
    }

    private void saveTag(final TagDTO tagDTO, final long dateiId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "insert ignore into Tags (tag_name)" + " values (?)", Statement.RETURN_GENERATED_KEYS);

        preparedStatement.setString(1, tagDTO.getText());
        preparedStatement.execute();

        ResultSet id = preparedStatement.getGeneratedKeys();
        if ((id.next())) { //true = not empty false = empty
            saveTagnutzung(dateiId, id.getLong(1));
        } else {
            saveTagnutzung(dateiId, getTagIdByTagname(tagDTO.getText()));
        }

        preparedStatement.close();
        id.close();
    }

    private long getTagIdByTagname(final String tagName) throws SQLException {
        long tagId = -1;
        PreparedStatement preparedStatement =
                connection.prepareStatement("select tagID from Tags where tag_name=?");
        preparedStatement.setString(1, tagName);

        ResultSet idResult = preparedStatement.executeQuery();
        if (idResult.next()) {
            tagId = idResult.getLong("tagID");
        }

        preparedStatement.close();
        idResult.close();

        return tagId;
    }

    private TagDTO findTagById(final long id) throws SQLException {
        TagDTO tag = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Tags where tagID=?");
        preparedStatement.setString(1, "" + id);

        ResultSet tagResult = preparedStatement.executeQuery();

        if (tagResult.next()) {
            tag = new TagDTO(tagResult.getLong("tagID"), tagResult.getString("tag_name"));
        }

        preparedStatement.close();
        tagResult.close();

        return tag;
    }

    private LinkedList<TagDTO> findAllTagsbyDateiId(final long dateiId) throws SQLException {
        LinkedList<TagDTO> tags = new LinkedList<>();

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Tagnutzung where dateiID=?");
        preparedStatement.setString(1, "" + dateiId);

        ResultSet tagResult = preparedStatement.executeQuery();

        while (tagResult.next()) {
            tags.add(findTagById(tagResult.getLong("tagID")));
        }

        preparedStatement.close();
        tagResult.close();

        return tags;
    }


    void deleteTagRelationsByDateiId(final long dateiId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Tagnutzung where dateiID=?");
        preparedStatement.setLong(1, dateiId);

        preparedStatement.execute();

        preparedStatement.close();
    }

    boolean doTagsExistByDateiId(final long dateiId) throws SQLException {

        PreparedStatement preparedStatement =
                connection.prepareStatement("select tagID from Tagnutzung where dateiID=?");
        preparedStatement.setLong(1, dateiId);

        ResultSet result = preparedStatement.executeQuery();
        boolean doesExist = result.next();
        result.close();
        preparedStatement.close();

        return doesExist;
    }

    /*
        GRUPPE METHODS
     */

    @SuppressWarnings("checkstyle:magicnumber")
    private void saveGruppe(final GruppeDTO gruppeDTO) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "insert ignore into Gruppe (gruppeID, titel, beschreibung)" + " values (?, ?, ?)");

        preparedStatement.setString(1, gruppeDTO.getId());
        preparedStatement.setString(2, gruppeDTO.getName());
        preparedStatement.setString(3, gruppeDTO.getDescription());
        preparedStatement.execute();

        preparedStatement.close();
    }


    @SuppressWarnings("checkstyle:MagicNumber")
    private void saveGruppenbelegung(final long userId,
                                     final String gruppeId, final boolean berechtigung) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "insert ignore into Gruppenbelegung (upload_berechtigung,"
                                + "gruppeID, userID)" + " values (?, ?, ?)");

        preparedStatement.setBoolean(1, berechtigung);
        preparedStatement.setString(2, gruppeId);
        preparedStatement.setLong(3, userId);
        preparedStatement.execute();

        preparedStatement.close();
    }

    private HashMap<GruppeDTO, Boolean> findAllGruppeByUserID(final long userId) throws SQLException {
        HashMap<GruppeDTO, Boolean> gruppen = new HashMap<>();

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Gruppenbelegung where userID=?");
        preparedStatement.setString(1, "" + userId);
        ResultSet gruppenResult = preparedStatement.executeQuery();

        while (gruppenResult.next()) {
            gruppen.put(findGruppeByGruppeId(gruppenResult.getString("gruppeID")),
                    gruppenResult.getBoolean("upload_berechtigung"));
        }

        preparedStatement.close();
        gruppenResult.close();

        return gruppen;
    }

    GruppeDTO findGruppeByGruppeId(final String gruppeId) throws SQLException {
        GruppeDTO gruppe = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Gruppe where gruppeID=?");
        preparedStatement.setString(1, "" + gruppeId);

        ResultSet gruppeResult = preparedStatement.executeQuery();

        if (gruppeResult.next()) {
            gruppe = new GruppeDTO(gruppeId,
                    gruppeResult.getString("titel"),
                    gruppeResult.getString("beschreibung"),
                    new LinkedList<>(), this);
        }
        preparedStatement.close();
        gruppeResult.close();

        return gruppe;
    }

    GruppeDTO findGruppeByGruppeDTOEager(final GruppeDTO gruppeDTO) throws SQLException {
        GruppeDTO gruppe = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Gruppe where gruppeID=?");
        preparedStatement.setString(1, "" + gruppeDTO);

        ResultSet gruppeResult = preparedStatement.executeQuery();

        if (gruppeResult.next()) {
            gruppe = new GruppeDTO(gruppeDTO.getId(),
                    gruppeResult.getString("titel"),
                    gruppeResult.getString("beschreibung"),
                    findAllDateiByGruppeDTO(gruppeDTO), this);

            for (DateiDTO datei : gruppe.getDateien()) {
                datei.setGruppe(gruppe);
            }
        }
        preparedStatement.close();
        gruppeResult.close();

        return gruppe;
    }

    void deleteUserGroupRelationByGroupId(final String gruppeId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Gruppenbelegung where gruppeID=?");
        preparedStatement.setString(1, gruppeId);

        preparedStatement.execute();

        preparedStatement.close();
    }

    void deleteUserGroupRelationByUserId(final long userId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Gruppenbelegung where userID=?");
        preparedStatement.setLong(1, userId);

        preparedStatement.execute();

        preparedStatement.close();
    }

    boolean doGroupRelationsExistByUserId(final long userId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("select gruppeID from Gruppenbelegung where userID=?");
        preparedStatement.setLong(1, userId);

        ResultSet result = preparedStatement.executeQuery();
        boolean doesExist = result.next();
        result.close();
        preparedStatement.close();

        return doesExist;
    }

    boolean doGroupRelationsExistByGruppeId(final String gruppeId) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("select userID from Gruppenbelegung where gruppeID=?");
        preparedStatement.setString(1, gruppeId);

        ResultSet result = preparedStatement.executeQuery();
        boolean doesExist = result.next();
        result.close();
        preparedStatement.close();

        return doesExist;
    }

    /*
        USER METHODS
     */

    @SuppressWarnings("checkstyle:MagicNumber")
    UserDTO findUserByKeycloaknameEager(final String keyCloakNameArg) throws SQLException {
        UserDTO user = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from User where key_cloak_name=?");
        preparedStatement.setString(1, keyCloakNameArg);

        ResultSet users = preparedStatement.executeQuery();

        if (users.next()) {
            user = new UserDTO(users.getLong("userID"),
                    users.getString("vorname"),
                    users.getString("nachname"),
                    users.getString("key_cloak_name"),
                    findAllGruppeByUserID(users.getLong("userID")));
        }

        preparedStatement.close();
        users.close();

        return user;
    }


    LinkedList<UserDTO> findAllUserByGruppeId(final String gruppeId) throws SQLException {
        LinkedList<UserDTO> users = new LinkedList<>();
        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from Gruppenbelegung where gruppeID=?");
        preparedStatement.setString(1, "" + gruppeId);

        ResultSet userResult = preparedStatement.executeQuery();
        while (userResult.next()) {
            users.add(findUserByIdLAZY(userResult.getLong("userID")));
        }

        preparedStatement.close();
        userResult.close();

        return users;
    }

    UserDTO findUserByIdLAZY(final long id) throws SQLException {
        UserDTO user = null;

        PreparedStatement preparedStatement =
                connection.prepareStatement("select * from User where userID=?");
        preparedStatement.setString(1, "" + id);

        ResultSet userResult = preparedStatement.executeQuery();
        if (userResult.next()) {
            user = new UserDTO(id, userResult.getString("vorname"),
                    userResult.getString("nachname"),
                    userResult.getString("key_cloak_name"),
                    null);
        }


        preparedStatement.close();
        userResult.close();
        return user;
    }

    @Scheduled(cron = " 0 0 0 * * *")
        //Clears Cache at midnight on everyday of the year
    void clearCache() {
        gruppeCache.clear();
    }

    /*
        TESTING METHOD
     */
    @SuppressWarnings("checkstyle:magicnumber")
    void deleteAll() throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement("delete from Gruppenbelegung");
        preparedStatement.execute();
        preparedStatement.close();

        preparedStatement =
                connection.prepareStatement("delete from Tagnutzung");
        preparedStatement.execute();
        preparedStatement.close();

        preparedStatement =
                connection.prepareStatement("delete from Datei");
        preparedStatement.execute();
        preparedStatement.close();

        preparedStatement =
                connection.prepareStatement("delete from Gruppe");
        preparedStatement.execute();
        preparedStatement.close();

        preparedStatement =
                connection.prepareStatement("delete from User");
        preparedStatement.execute();
        preparedStatement.close();

        preparedStatement =
                connection.prepareStatement("insert ignore into User (userID, vorname, nachname, key_cloak_name)"
                        + "values (?, ?, ?, ?)");
        preparedStatement.setString(1, "" + -1);
        preparedStatement.setString(2, "User");
        preparedStatement.setString(3, "Deleted");
        preparedStatement.setString(4, "-");
        preparedStatement.execute();

        preparedStatement.close();
    }

    public List<String> getUsersByGruppenId(final String gruppenId) throws SQLException {
        List<String> result = new ArrayList<>();
        PreparedStatement preparedStatement =
                connection.prepareStatement("SELECT  userID FROM Gruppenbelegung WHERE gruppeID=?");
        preparedStatement.setString(1, "" + gruppenId);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet.next()) {
            result.add(getKeyCloakNameByUserId(resultSet.getInt("userID")));
        }
        preparedStatement.close();
        return result;
    }

    public String getKeyCloakNameByUserId(final long id) throws SQLException {
        String name;
        PreparedStatement preparedStatement =
                connection.prepareStatement("SELECT  key_cloak_name FROM User WHERE userID=?");
        preparedStatement.setString(1, "" + id);
        ResultSet resultSet = preparedStatement.executeQuery();
        resultSet.next();
        name = resultSet.getString("key_cloak_name");
        preparedStatement.close();
        return name;
    }

    public long getStatus() throws SQLException {
        long status;
        PreparedStatement preparedStatement =
                connection.prepareStatement("SELECT  status FROM Status");
        ResultSet resultSet = preparedStatement.executeQuery();
        resultSet.next();
        status = resultSet.getInt("status");
        preparedStatement.close();
        return status;
    }

    public void updateStatus(final StatusDTO statusDTO) throws SQLException {
        PreparedStatement preparedStatement =
                connection.prepareStatement(
                        "update Status set status=?");

        preparedStatement.setLong(1, statusDTO.getStatus());
        preparedStatement.execute();
        preparedStatement.close();
    }

}
