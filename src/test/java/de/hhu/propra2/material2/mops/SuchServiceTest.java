package de.hhu.propra2.material2.mops;

import de.hhu.propra2.material2.mops.Database.DTOs.UserDTO;
import de.hhu.propra2.material2.mops.Database.DateiRepository;
import de.hhu.propra2.material2.mops.Database.GruppeRepository;
import de.hhu.propra2.material2.mops.Database.UserRepository;
import de.hhu.propra2.material2.mops.domain.models.Datei;
import de.hhu.propra2.material2.mops.domain.models.Gruppe;
import de.hhu.propra2.material2.mops.domain.models.Tag;
import de.hhu.propra2.material2.mops.domain.models.User;
import de.hhu.propra2.material2.mops.domain.services.ModelService;
import de.hhu.propra2.material2.mops.domain.models.Suche;
import de.hhu.propra2.material2.mops.domain.services.SuchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
public class SuchServiceTest {

    @Mock
    private DateiRepository dateiRepoMock;
    @Mock
    private GruppeRepository gruppenRepoMock;
    @Mock
    private UserRepository userRepoMock;
    @Mock
    private ModelService modelServiceMock;
    @Mock
    private User userMock;
    @Mock
    private Gruppe gruppeMock1;
    @Mock
    private Gruppe gruppeMock2;
    @Mock
    private User uploaderMock;

    private SuchService suchService;
    private List<Datei> dateienGruppe1;
    private List<Datei> dateienGruppe2;
    private Datei datei1;
    private Datei datei2;
    private Datei datei3;
    private Datei datei4;

    @BeforeEach
    public void setUp() {
        this.suchService = new SuchService(dateiRepoMock,
                gruppenRepoMock,
                userRepoMock,
                modelServiceMock);

        //TODO Mockito.lenient() = eig stub?
        Mockito.lenient().when(modelServiceMock.load(any(UserDTO.class))).thenReturn(userMock);

        //Date for Datei
        Calendar calender = Calendar.getInstance();
        calender.set(2020, 1, 3);
        Date date1 = calender.getTime();
        calender.set(2020, 3, 5);
        Date date2 = calender.getTime();
        calender.set(2020, 1, 1);
        Date veroeffentlichung = calender.getTime();

        //Tags for Datei
        Tag tag1 = new Tag(1, "Vorlesung");
        Tag tag2 = new Tag(2, "Relevant");
        Tag tag3 = new Tag(3, "Übung");
        Tag tag4 = new Tag(4, "Irgendwas");
        List<Tag> tags1 = new ArrayList<>(Arrays.asList(tag1, tag2, tag3, tag4));
        List<Tag> tags2 = new ArrayList<>(Arrays.asList(tag1));
        List<Tag> tags3 = new ArrayList<>(Arrays.asList(tag2, tag4));

        //Dateien for List<Datei>
        datei1 = new Datei(1, "1", "a/b/2", uploaderMock, tags1,
                date1, veroeffentlichung, 1, "pdf");
        datei2 = new Datei(2, "2", "a/b/2", uploaderMock, tags2,
                date1, veroeffentlichung, 1, "pdf");
        datei3 = new Datei(3, "3", "a/b/3", uploaderMock, tags3,
                date1, veroeffentlichung, 1, "jpg");
        datei4 = new Datei(4, "4", "a/b/4", uploaderMock, tags3,
                date2, veroeffentlichung, 1, "jpg");
        dateienGruppe1 = new ArrayList<>(Arrays.asList(datei1, datei2, datei3, datei4));
        dateienGruppe2 = new ArrayList<>(Arrays.asList(datei1, datei2, datei3));

        when(gruppeMock1.getDateien()).thenReturn(dateienGruppe1);
        when(gruppeMock2.getDateien()).thenReturn(dateienGruppe2);
        when(userMock.getAllGruppen()).thenReturn(Arrays.asList(gruppeMock1, gruppeMock2));
    }

    @Test
    public void keine_Filter() {
        Suche suche = new Suche(
                "01.01.2000",
                "31.12.2100",
                null,
                null,
                null,
                null,
                null);

        List<Datei> result = suchService.starteSuche(suche, "Peter", userMock);

        assertThat(result.size(), is(4));
//        assertThat(result, contains(Arrays.asList(datei1, datei2, datei3, datei4)));
        assertTrue(result.contains(datei1));
        assertTrue(result.contains(datei2));
        assertTrue(result.contains(datei3));
        assertTrue(result.contains(datei4));
    }

}
