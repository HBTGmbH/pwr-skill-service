package de.hbt.power.service;

import de.hbt.power.repo.SkillRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import de.hbt.power.model.Skill;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("test")
public class SkillSearcherService_ExpectedResultITest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillSearcherService skillSearcherService;

    private void persistentSkill(String name) {
        skillRepository.save(Skill.of(name));
    }

    @Test
    public void shouldFindQueryResult() throws Exception {
        skillRepository.deleteAll();
        persistentSkill("Java");
        persistentSkill("Unity");
        persistentSkill("Kotlin");
        skillSearcherService.buildSearchIndexSync();
        shouldFind("Jav", "Java");
        shouldFind("java", "Java");
        shouldFind("Java", "Java");
        shouldFind("ja", "Java");
        shouldFind("Ja", "Java");
        shouldFind("java", "Java");
        shouldFind("jAv", "Java");
        shouldFind("J", "Java");
    }

    private void shouldFind(String query, String result) {
        List<String> queryResults = skillSearcherService.searchSkill(query, 100);
        assertThat(queryResults).as("Should find " + result + " for query " + query).containsExactly(result);
    }
}
