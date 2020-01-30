package de.hbt.power.controller;

import de.hbt.power.exception.SkillServiceException;
import de.hbt.power.model.Skill;
import de.hbt.power.model.SkillCategory;
import de.hbt.power.model.dto.TCategoryNode;
import de.hbt.power.repo.SkillCategoryRepository;
import de.hbt.power.repo.SkillRepository;
import de.hbt.power.service.CategoryService;
import de.hbt.power.service.SkillSearcherService;
import de.hbt.power.service.SkillService;
import de.hbt.power.service.SkillTreeMappingService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static de.hbt.power.exception.SkillServiceException.categoryNotFound;
import static de.hbt.power.exception.SkillServiceException.skillNotFound;
import static de.hbt.power.util.SkillServiceUtil.peek;


/**
 * Contains mappings for the skill resource.
 */
@RestController()
@RequestMapping("/skill")
/*
@CrossOrigin(origins = "*",
        methods = {RequestMethod.PUT, RequestMethod.GET, RequestMethod.PATCH, RequestMethod.POST, RequestMethod.DELETE},
        allowedHeaders = {"origin", "content-type", "accept", "authorization", "X-Requested-With"},
        allowCredentials = "true")*/
@CrossOrigin
@Log4j2
public class SkillController {

    private static final Integer DEFAULT_MAX_RESULTS = 20;

    private final SkillRepository skillRepository;

    private final SkillCategoryRepository skillCategoryRepository;

    private final SkillSearcherService skillSearcherService;

    private final SkillService skillService;

    private final CategoryService categoryService;

    private final SkillTreeMappingService skillTreeMappingService;

    @Autowired
    public SkillController(
            SkillRepository skillRepository,
            SkillCategoryRepository skillCategoryRepository,
            SkillSearcherService skillSearcherService, SkillService skillService, CategoryService categoryService, SkillTreeMappingService skillTreeMappingService) {
        this.skillRepository = skillRepository;
        this.skillCategoryRepository = skillCategoryRepository;
        this.skillSearcherService = skillSearcherService;
        this.skillService = skillService;
        this.categoryService = categoryService;
        this.skillTreeMappingService = skillTreeMappingService;
    }

    private Skill requireSkill(Integer id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> skillNotFound(id));
    }

    private SkillCategory requireSkillCategory(Integer id) {
        return skillCategoryRepository.findById(id)
                .orElseThrow(() -> categoryNotFound(id));
    }

    @ApiOperation(value = "Returns a skill by ID", response = Skill.class)
    @GetMapping("/{skillId}")
    public ResponseEntity<Skill> getSkill(@PathVariable Integer skillId) {
        return ResponseEntity.ok(requireSkill(skillId));
    }


    @ApiOperation(value = "Moves a skill to a different category", response = Skill.class)
    @PatchMapping("/{id}/category/{category_id}")
    public ResponseEntity<Skill> moveSkill(@PathVariable("id") Integer skillId, @PathVariable("category_id") Integer newParentId) {
        Skill skill = requireSkill(skillId);
        SkillCategory skillCategory = requireSkillCategory(newParentId);
        log.info("Moving " + skill + " to " + skillCategory);
        skill = skillService.moveSkillToCategory(skill, skillCategory);
        return ResponseEntity.ok(skill);
    }


    @ApiOperation(value = "Returns all skill IDs", response = Integer.class, responseContainer = "List")
    @GetMapping
    public ResponseEntity<List<Integer>> getAllSkillIds() {
        final List<Integer> result = new ArrayList<>();
        skillRepository.findAll().forEach(skill -> result.add(skill.getId()));
        return ResponseEntity.ok(result);
    }


    @ApiOperation(value = "Updates a skill", response = Skill.class)
    @PutMapping("/{id}")
    public ResponseEntity<Skill> updateSkill(@PathVariable Integer id, @RequestBody Skill skill) {
        Skill s = requireSkill(id);
        skill.setId(s.getId());
        skillRepository.save(s);
        return ResponseEntity.ok().build();
    }


    @ApiOperation(value = "Searches and returns a skill by qualifier", response = Skill.class)
    @GetMapping("/byName")
    public ResponseEntity<Skill> findSkillByQualifier(@RequestParam("qualifier") String qualifier) {
        return skillRepository.findOneByQualifier(qualifier)
                .map((skill) -> {
                    // Trick/Hack: ensure parent categories are loaded by hibernate (lazy) - kr
                    SkillCategory cat = skill.getCategory();
                    while (cat != null && cat.getCategory() != null) {
                        cat = cat.getCategory();
                    }
                    return skill;
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build()); // TODO find by localized qualifieres
    }


    @ApiOperation(value = "Fuzzy search for skill names", response = String.class, responseContainer = "List")
    @GetMapping(value = "/search")
    public ResponseEntity<List<String>> searchSkill(@RequestParam("searchterm") String searchTerm, @RequestParam Integer maxResults) {
        if (maxResults == null) maxResults = DEFAULT_MAX_RESULTS;
        List<String> suggestions = skillSearcherService.searchSkill(searchTerm, maxResults);
        return ResponseEntity.ok(suggestions);
    }


    @ApiOperation(value = "Attempty to automatically group a skill into a category", response = Skill.class)
    @PatchMapping("/{id}")
    public ResponseEntity<Skill> categorizeSkill(@PathVariable Integer id) {
        Skill responseSkill = requireSkill(id);
        log.info("Categorizing " + responseSkill.toString());
        responseSkill = categoryService.categorizeSkill(responseSkill);
        return ResponseEntity.ok(responseSkill);
    }

    /**
     * Categorizes a list of skills if no SkillCategory was set before.
     * Creates respective skills if they don't exist yet.
     * Returns a List of all updated skills.
     *
     * @return List of updated skills
     */
    @ApiOperation(value = "Updates the category of the given skill. If no skill was found, the skill is created", response = SkillCategory.class)
    @PostMapping("/categorize")
    public ResponseEntity<List<Skill>> updateAndGetCategories(@RequestParam(value="list") String[] qualifiers) {

        Set<String> distinctQualifiers = new HashSet<>(Arrays.asList(qualifiers));
        //List of all skills with any one of the given qualifiers in the database
        List<Skill> existingSkills = skillRepository.findAllByQualifierIn(distinctQualifiers);
        //Set of all given qualifiers found in the database
        Set<String> existingSkillQualifiers = existingSkills.stream()
                .map(Skill::getQualifier)
                .collect(Collectors.toSet());
        //Filtering all Skills without a category from existing skills
        List<Skill> skillsWithoutCategory = existingSkills.stream()
                .filter(skill -> skill.getCategory() == null)
                .collect(Collectors.toList());
        //Creating all skills that don't exist yet
        Set<String> newSkills = new HashSet<>(distinctQualifiers);
        newSkills.removeAll(existingSkillQualifiers);
        for (String qualifier : newSkills) {
            skillsWithoutCategory.add(skillService.createSkill(qualifier));
        }
        //Categorizing all skills that don't have a category yet, including those that were just created
        if (!skillsWithoutCategory.isEmpty()) {
            for (Skill s : skillsWithoutCategory) {
                Skill categorizedSkill = categoryService.categorizeSkill(s);
                skillRepository.save(categorizedSkill);
            }
        }

        return ResponseEntity.ok(skillsWithoutCategory);
    }

    /**
     * Returns the category for the requested SkillId, categorizes the Skill if no SkillCategory was set before.
     *
     * @return updated SkillCategory
     */
    @ApiOperation(value = "Updates the category of the given skill. If no skill was found, the skill is created", response = SkillCategory.class)
    @PostMapping
    public ResponseEntity<SkillCategory> updateAndGetCategory(@RequestParam("qualifier") String qualifier) {
        Skill skill = skillRepository.findOneByQualifier(qualifier)
                .orElseGet(() -> skillService.createSkill(qualifier));
        if (skill.getCategory() == null) {
            log.info("Categorizing " + skill.toString());
            skill = categoryService.categorizeSkill(skill);
        }
        return ResponseEntity.ok(skill.getCategory());
    }


    @ApiOperation(value = "Rebuilds the lucene index")
    @PostMapping("/lucene/index")
    public void buildIndex() {
        log.info("Rebuilding Lucene index.");
        skillSearcherService.buildSearchIndex();
    }


    @ApiOperation(value = "Creates a new skill in the provided category", response = Skill.class)
    @PostMapping("/category/{categoryId}")
    public ResponseEntity<Skill> createSkillInCategory(@RequestBody Skill skill, @PathVariable("categoryId") Integer categoryId) {
        SkillCategory skillCategory = requireSkillCategory(categoryId);
        Optional<Skill> concurrent = skillRepository.findOneByQualifier(skill.getQualifier());
        if (concurrent.isPresent()) {
            throw SkillServiceException.skillAlreadyExists(concurrent.get());
        }
        log.info("Creating " + skill.toString() + " in " + skillCategory.toString());
        skill.setId(null);
        skillService.createSkillInCategory(skill, skillCategory);
        return ResponseEntity.ok(skill);
    }


    @ApiOperation(value = "Permanently deletes a skill")
    @DeleteMapping("/{id}")
    public ResponseEntity deleteSkill(@PathVariable("id") Integer skillId) {
        skillRepository.findById(skillId)
                .map(peek(s -> log.info("Deleting skill " + s)))
                .ifPresent(skillRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @ApiOperation(value = "Adds a locale to the given skill")
    @PostMapping("/{id}/locale/{language}")
    public ResponseEntity<Skill> addLocale(@PathVariable("id") Integer skillId,
                                           @PathVariable("language") String language,
                                           @RequestParam("qualifier") String qualifier) {
        Skill skill = requireSkill(skillId);
        log.info("Adding locale{language='" + language + "', qualifier='" + qualifier + "'} to " + skill.toString());
        skillService.addLocaleToSkill(skill, qualifier, language);
        return ResponseEntity.ok(skill);
    }

    @ApiOperation(value = "Deletes a locale from the given skill")
    @DeleteMapping("/{id}/locale/{language}")
    public ResponseEntity<Skill> deleteLocale(@PathVariable("id") Integer skillId, @PathVariable("language") String language) {
        Skill skill = requireSkill(skillId);
        log.info("Removing locale for language '" + language + "' from " + skill.toString());
        skillService.removeLocaleFromSkill(skill, language);
        return ResponseEntity.ok(skill);
    }

    @ApiOperation(value = "Adds a version to a skill")
    @PostMapping("{id}/version")
    public ResponseEntity<Set<String>> addVersion(@PathVariable("id") Integer skillId, @RequestBody String version) {
        Skill skill = requireSkill(skillId);
        skillService.addVersion(skill, version);
        return ResponseEntity.ok(skill.getVersions());
    }

    @ApiOperation(value = "Deletes a version from a skill")
    @DeleteMapping("{id}/version")
    public ResponseEntity deleteVersion(@PathVariable("id") Integer skillId, @RequestBody String version) {
        Skill skill = requireSkill(skillId);
        skillService.deleteVersion(skill, version);
        return ResponseEntity.ok().build();
    }

    @ApiOperation(value = "Returns a model of the skill tree. Returned value is the root node.", response = TCategoryNode.class)
    @GetMapping("/tree")
    public ResponseEntity<TCategoryNode> getTree() {
        return ResponseEntity.ok(skillTreeMappingService.buildSkillTree());
    }

    @ApiOperation(value = "Returns a model of the skill tree. Returned value is the root node.", response = TCategoryNode.class)
    @GetMapping("/tree/debug")
    public ResponseEntity<TCategoryNode> getTreeDebug() {
        return ResponseEntity.ok(skillTreeMappingService.buildSkillTreeDebug());
    }
}
