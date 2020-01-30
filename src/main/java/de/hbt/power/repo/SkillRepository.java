package de.hbt.power.repo;

import de.hbt.power.model.Skill;
import de.hbt.power.model.SkillCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Integer>, CrudRepository<Skill, Integer> {
    Optional<Skill> findOneByQualifier(String qualifier);

    Optional<Skill> getByQualifiersContaining(String localizedQualifiers);

    List<Skill> findAllByCategory_Id(Integer categoryId);

    List<Skill> findAllByQualifierInAndCategory_Id(Collection<String> qualifier, Integer categoryId);

    List<Skill> findAllByQualifierIn(Collection<String> qualifier);

    List<Skill> findAllByCategory(SkillCategory category);

    void deleteAllByCategory(SkillCategory category);

    List<Skill> findTop100ById(Integer id);

    List<Skill> findFirst50ByOrderById();

    @Query("select s from Skill s WHERE not s.category is null")
    List<Skill> findForTree();
}
