package de.hhu.propra2.material2.mops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Material2Application.class)
class Material2ApplicationTests {
    /**
     * Default true-throwing test.  
     */
    @Test
    void contextLoads() {
        assertEquals(1, 1); 
    }

}
