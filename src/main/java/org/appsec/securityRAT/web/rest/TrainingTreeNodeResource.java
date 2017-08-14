package org.appsec.securityRAT.web.rest;

import com.codahale.metrics.annotation.Timed;
import org.appsec.securityRAT.domain.*;
import org.appsec.securityRAT.repository.*;
import org.appsec.securityRAT.repository.search.TrainingTreeNodeSearchRepository;
import org.appsec.securityRAT.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * REST controller for managing TrainingTreeNode.
 */
@RestController
@RequestMapping("/api")
public class TrainingTreeNodeResource {

    private final Logger log = LoggerFactory.getLogger(TrainingTreeNodeResource.class);

    @Inject
    private TrainingRepository trainingRepository;

    @Inject
    private TrainingTreeNodeRepository trainingTreeNodeRepository;

    @Inject
    private TrainingTreeNodeSearchRepository trainingTreeNodeSearchRepository;

    @Inject
    private TrainingCustomSlideNodeRepository trainingCustomSlideNodeRepository;

    @Inject
    private TrainingGeneratedSlideNodeRepository trainingGeneratedSlideNodeRepository;

    @Inject
    private TrainingRequirementNodeRepository trainingRequirementNodeRepository;

    @Inject
    private TrainingBranchNodeRepository trainingBranchNodeRepository;

    @Inject
    private TrainingCategoryNodeRepository trainingCategoryNodeRepository;

    /**
     * POST  /trainingTreeNodes -> Create a new trainingTreeNode.
     */
    @RequestMapping(value = "/trainingTreeNodes",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<TrainingTreeNode> create(@RequestBody TrainingTreeNode trainingTreeNode) throws URISyntaxException {
        log.debug("REST request to save TrainingTreeNode : {}", trainingTreeNode);
        if (trainingTreeNode.getId() != null) {
            return ResponseEntity.badRequest().header("Failure", "A new trainingTreeNode cannot already have an ID").body(null);
        }
        TrainingTreeNode result = trainingTreeNodeRepository.save(trainingTreeNode);
        trainingTreeNodeSearchRepository.save(result);
        return ResponseEntity.created(new URI("/api/trainingTreeNodes/" + result.getId()))
            .headers(new HttpHeaders())
            .body(result);
    }

    /**
     * PUT  /trainingTreeNodes -> Updates an existing trainingTreeNode.
     */
    @RequestMapping(value = "/trainingTreeNodes",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<TrainingTreeNode> update(@RequestBody TrainingTreeNode trainingTreeNode) throws URISyntaxException {
        log.debug("REST request to update TrainingTreeNode : {}", trainingTreeNode);
        if (trainingTreeNode.getId() == null) {
            return create(trainingTreeNode);
        }
        TrainingTreeNode result = trainingTreeNodeRepository.save(trainingTreeNode);
        trainingTreeNodeSearchRepository.save(trainingTreeNode);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert("trainingTreeNode", trainingTreeNode.getId().toString()))
                .body(result);
    }

    /**
     * GET  /trainingTreeNodes -> get all the trainingTreeNodes.
     */
    @RequestMapping(value = "/trainingTreeNodes",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<TrainingTreeNode> getAll() {
        log.debug("REST request to get all TrainingTreeNodes");
        return trainingTreeNodeRepository.findAll();
    }

    /**
     * GET  /trainingTreeNodes/:id -> get the "id" trainingTreeNode.
     */
    @RequestMapping(value = "/trainingTreeNodes/{id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<TrainingTreeNode> get(@PathVariable Long id) {
        log.debug("REST request to get TrainingTreeNode : {}", id);
        TrainingTreeNode result = trainingTreeNodeRepository.findOne(id);

        // add reverse relations
        switch(result.getNode_type()) {
            case CustomSlideNode:
                TrainingCustomSlideNode customSlideNode = trainingCustomSlideNodeRepository.getTrainingCustomSlideNodeByTrainingTreeNode(result);
                result.setCustomSlideNode(customSlideNode);
                break;
            case BranchNode:
                TrainingBranchNode branchNode = trainingBranchNodeRepository.getTrainingBranchNodeByTrainingTreeNode(result);
                result.setBranchNode(branchNode);
                break;
            case RequirementNode:
                result.setRequirementNode(trainingRequirementNodeRepository.getTrainingRequirementNodeByTrainingTreeNode(result));
                result.getRequirementNode().getRequirementSkeleton().getShortName();
                break;
            case GeneratedSlideNode:
                result.setGeneratedSlideNode(trainingGeneratedSlideNodeRepository.getTrainingGeneratedSlideNodeByTrainingTreeNode(result));
                break;
            case CategoryNode:
                TrainingCategoryNode categoryNode = trainingCategoryNodeRepository.getTrainingCategoryNodeByTrainingTreeNode(result);
                result.setCategoryNode(categoryNode);
                break;
        }

        List<TrainingTreeNode> children = trainingTreeNodeRepository.getChildrenOf(result);
        List<TrainingTreeNode> childrenResult = new ArrayList<>();
        for(TrainingTreeNode child : children) {
            childrenResult.add(get(child.getId()).getBody());
        }
        result.setChildren(childrenResult);

        return Optional.ofNullable(result)
            .map(trainingTreeNode -> new ResponseEntity<>(
                trainingTreeNode,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /trainingTreeNodes/:id -> delete the "id" trainingTreeNode.
     */
    @RequestMapping(value = "/trainingTreeNodes/{id}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("REST request to delete TrainingTreeNode : {}", id);

        TrainingTreeNode trainingTreeNode = trainingTreeNodeRepository.findOne(id);

        trainingTreeNodeRepository.delete(id);
        trainingTreeNodeSearchRepository.delete(id);

        // delete special table entry
        switch(trainingTreeNode.getNode_type()) {
            case BranchNode:
                TrainingBranchNode branchNode = trainingBranchNodeRepository.getTrainingBranchNodeByTrainingTreeNode(trainingTreeNode);
                trainingBranchNodeRepository.delete(branchNode.getId());
                break;
            case CustomSlideNode:
                TrainingCustomSlideNode customSlideNode = trainingCustomSlideNodeRepository.getTrainingCustomSlideNodeByTrainingTreeNode(trainingTreeNode);
                trainingCustomSlideNodeRepository.delete(customSlideNode);
                break;
            case CategoryNode:
                TrainingCategoryNode categoryNode = trainingCategoryNodeRepository.getTrainingCategoryNodeByTrainingTreeNode(trainingTreeNode);
                trainingCategoryNodeRepository.delete(categoryNode);
                break;
            case RequirementNode:
                TrainingRequirementNode requirementNode = trainingRequirementNodeRepository.getTrainingRequirementNodeByTrainingTreeNode(trainingTreeNode);
                trainingRequirementNodeRepository.delete(requirementNode);
                break;
            case GeneratedSlideNode:
                TrainingGeneratedSlideNode generatedSlideNode = trainingGeneratedSlideNodeRepository.getTrainingGeneratedSlideNodeByTrainingTreeNode(trainingTreeNode);
                trainingGeneratedSlideNodeRepository.delete(generatedSlideNode);
                break;
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("trainingTreeNode", id.toString())).build();
    }

    /**
     * SEARCH  /_search/trainingTreeNodes/:query -> search for the trainingTreeNode corresponding
     * to the query.
     */
    @RequestMapping(value = "/_search/trainingTreeNodes/{query}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<TrainingTreeNode> search(@PathVariable String query) {
        return StreamSupport
            .stream(trainingTreeNodeSearchRepository.search(queryString(query)).spliterator(), false)
            .collect(Collectors.toList());
    }

    /**
     * Get the rootNode of a training
     */
    @RequestMapping(value = "/TrainingTreeNode/rootNode/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<TrainingTreeNode> getTrainingRoot(@PathVariable Long id) {
        log.debug("REST request to get the rootNode of the training with id : {}", id);
        Training training = trainingRepository.getOne(id);
        TrainingTreeNode result = trainingTreeNodeRepository.getTrainingRoot(training);
        return ResponseEntity.ok()
            .headers(new HttpHeaders())
            .body(result);
    }

    /**
     * Get all children of a trainingTreeNode
     */
    @RequestMapping(value = "/TrainingTreeNode/childrenOf/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<TrainingTreeNode>> getChildrenOf(@PathVariable Long id) {
        log.debug("REST request to get all children of TrainingTreeNode with id : {}", id);
        TrainingTreeNode node = trainingTreeNodeRepository.getOne(id);
        List<TrainingTreeNode> result = trainingTreeNodeRepository.getChildrenOf(node);
        return ResponseEntity.ok()
            .headers(new HttpHeaders())
            .body(result);
    }
}
