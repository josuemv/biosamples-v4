package uk.ac.ebi.biosamples.legacy.json.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biosamples.legacy.json.domain.SamplesRelations;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.legacy.json.service.SampleRelationsResourceAssembler;
import uk.ac.ebi.biosamples.legacy.json.repository.SampleRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/samplesrelations", produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@ExposesResourceFor(SamplesRelations.class)
public class SamplesRelationsController {

    private final SampleRepository sampleRepository;
    private final SampleRelationsResourceAssembler relationsResourceAssembler;

    @Autowired
    EntityLinks entityLinks;

    public SamplesRelationsController(SampleRepository sampleRepository,
                                      SampleRelationsResourceAssembler relationsResourceAssembler) {

        this.sampleRepository = sampleRepository;
        this.relationsResourceAssembler = relationsResourceAssembler;

    }

    @GetMapping
    public PagedResources<Resource<SamplesRelations>> allSamplesRelations(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            PagedResourcesAssembler<SamplesRelations> pagedResourcesAssembler) {


        PagedResources<Resource<Sample>> samples = sampleRepository.getPagedSamples(page, size);
        List<SamplesRelations> legacyRelationsResources = samples.getContent().stream()
                .map(Resource::getContent)
                .map(SamplesRelations::new)
                .collect(Collectors.toList());
        Pageable pageRequest = new PageRequest(page, size);
        Page<SamplesRelations> pageResources = new PageImpl<>(legacyRelationsResources, pageRequest, samples.getMetadata().getTotalElements());

        return pagedResourcesAssembler.toResource(pageResources, this.relationsResourceAssembler, entityLinks.linkToCollectionResource(SamplesRelations.class));
    }




}

