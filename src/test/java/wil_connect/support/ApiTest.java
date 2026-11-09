package wil_connect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import wil_connect.security.AttemptLimiter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * Base for the API tests. They run against the MySQL database loaded with
 * schema.sql and seed.sql, and every test is rolled back afterwards.
 *
 * <p>Seed accounts (password "password"): John Smith (student 1, placement 1),
 * Sarah Jones (student 2, no placement), Michael Brown (supervisor 1, PIN 1234),
 * Linda Davis (supervisor 2, no students), Nexus Tech Solutions (employer 1, Approved),
 * BrightWorks Solutions (employer 2, Pending) and the coordinator.</p>
 */
@SpringBootTest(properties = {
        "wil.upload.dir=target/test-uploads/proof",
        // Tests decide themselves which files exist.
        "wil.upload.create-missing-samples=false"
})
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTest {

    protected static final RequestPostProcessor JOHN = httpBasic("john.smith@richfield.co.za", "password");
    protected static final RequestPostProcessor SARAH = httpBasic("sarah.jones@richfield.co.za", "password");
    protected static final RequestPostProcessor MICHAEL = httpBasic("michael.brown@richfield.co.za", "password");
    protected static final RequestPostProcessor LINDA = httpBasic("linda.davis@richfield.co.za", "password");
    protected static final RequestPostProcessor NEXUS = httpBasic("hr.nexus@richfield.co.za", "password");
    protected static final RequestPostProcessor BRIGHTWORKS = httpBasic("hr.brightworks@richfield.co.za", "password");
    protected static final RequestPostProcessor COORDINATOR = httpBasic("coordinator@richfield.co.za", "password");

    /** User ids from seed.sql */
    protected static final int JOHN_USER = 2;
    protected static final int MICHAEL_USER = 4;
    protected static final int COORDINATOR_USER = 1;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected AttemptLimiter attemptLimiter;

    /** The lockout counters live in memory, outside the rolled back transaction. */
    @AfterEach
    void clearLockouts() {
        attemptLimiter.reset("pin:1");
        attemptLimiter.reset("pin:2");
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    protected String message(MvcResult result) throws Exception {
        return json(result).path("message").asText();
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    protected String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    /** Keeps John's placement open until 30 days from today, so "today" is a valid log date. */
    protected void openJohnsPlacementUntilNextMonth() {
        jdbc.update("UPDATE PLACEMENT SET end_date = CURRENT_DATE + INTERVAL 30 DAY WHERE placement_id = 1");
    }
}
