package uk.ac.ebi.biosamples.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.PagedResources.PageMetadata;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.model.filter.Filter;
import uk.ac.ebi.biosamples.service.BioSamplesAapService;
import uk.ac.ebi.biosamples.service.FilterService;
import uk.ac.ebi.biosamples.service.SampleManipulationService;
import uk.ac.ebi.biosamples.service.SamplePageService;
import uk.ac.ebi.biosamples.service.SampleResourceAssembler;
import uk.ac.ebi.biosamples.service.SampleService;
import uk.ac.ebi.biosamples.solr.repo.CursorArrayList;
import uk.ac.ebi.biosamples.utils.LinkUtils;

/**
 * Primary controller for REST operations both in JSON and XML and both read and
 * write.
 * 
 * See {@link SampleHtmlController} for the HTML equivalent controller.
 * 
 * @author faulcon
 *
 */
@RestController
@ExposesResourceFor(Sample.class)
@RequestMapping("/samples")
public class SamplesRestController {

	private final SamplePageService samplePageService;
	private final SampleService sampleService;
	private final FilterService filterService;
	private final BioSamplesAapService bioSamplesAapService;
	private final SampleManipulationService sampleManipulationService;
	
	private final SampleResourceAssembler sampleResourceAssembler;

	private Logger log = LoggerFactory.getLogger(getClass());

	public SamplesRestController(
			SamplePageService samplePageService,FilterService filterService,
			BioSamplesAapService bioSamplesAapService,
			SampleResourceAssembler sampleResourceAssembler,
			SampleManipulationService sampleManipulationService,
			SampleService sampleService) {
		this.samplePageService = samplePageService;
		this.filterService = filterService;
		this.bioSamplesAapService = bioSamplesAapService;
		this.sampleResourceAssembler = sampleResourceAssembler;
		this.sampleManipulationService = sampleManipulationService;
		this.sampleService = sampleService;
	}
	
	private String decodeText(String text) {
		if (text != null) {
			try {
				//URLDecoder doesn't work right...
				//text = URLDecoder.decode(text, "UTF-8");
				text = UriUtils.decode(text, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}		
		return text;		
	}
	
	private String[] decodeFilter(String[] filter) {
		if (filter != null) {
			for (int i = 0; i < filter.length; i++) {
				try {
					//URLDecoder doesn't work right...
					//filter[i] = URLDecoder.decode(filter[i], "UTF-8");
					filter[i] = UriUtils.decode(filter[i], "UTF-8");
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return filter;
	}

	@CrossOrigin(methods = RequestMethod.GET)
	@GetMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE })
	public Resources<Resource<Sample>> searchHal(
			@RequestParam(name = "text", required = false) String text,
			@RequestParam(name = "filter", required = false) String[] filter, 
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "page", required = false) final Integer page,
			@RequestParam(name = "size", required = false) final Integer size, 
			@RequestParam(name = "sort", required = false) final String[] sort, 
			PagedResourcesAssembler<Sample> pageAssembler) {

		
		//Need to decode the %20 and similar from the parameters
		//this is *not* needed for the html controller
		String decodedText = decodeText(text);
		String[] decodedFilter = decodeFilter(filter);
		String decodedCursor = decodeText(cursor);
			
		int effectivePage;
		if (page == null) {
			effectivePage = 0;
		} else {
			effectivePage = page;
		}
		int effectiveSize;
		if (size == null) {
			effectiveSize = 20;
		} else {
			effectiveSize = size;
		}
		
		Collection<Filter> filters = filterService.getFiltersCollection(decodedFilter);
		Collection<String> domains = bioSamplesAapService.getDomains();

		if (cursor != null) {

			log.trace("This cursor = "+decodedCursor);
			CursorArrayList<Sample> samples = samplePageService.getSamplesByText(decodedText, filters, 
				domains, decodedCursor, effectiveSize);
			log.trace("Next cursor = "+samples.getNextCursorMark());
			
			Resources<Resource<Sample>>  resources = new Resources<>(samples.stream()
				.map(s -> sampleResourceAssembler.toResource(s))
				.collect(Collectors.toList()));

			resources.add(getCursorLink(decodedText, decodedFilter, decodedCursor, effectiveSize, Link.REL_SELF));
			//only display the next link if there is a next cursor to go to
			if (!decodeText(samples.getNextCursorMark()).equals(decodedCursor) 
					&& !samples.getNextCursorMark().equals("*")) {
				resources.add(getCursorLink(decodedText, decodedFilter, samples.getNextCursorMark(), effectiveSize, Link.REL_NEXT));				
			}
			
			return resources;
			
		} else {	
			
			String effectiveSort[] = sort;
			if (sort == null) {
				//if there is no existing sort, sort by score then accession
				effectiveSort = new String[2];
				effectiveSort[0] = "score,desc";
				effectiveSort[1] = "id,asc";
			} 
			Sort pageSort = new Sort(Arrays.stream(effectiveSort).map(this::parseSort).collect(Collectors.toList()));
			Pageable pageable = new PageRequest(effectivePage, effectiveSize, pageSort);
			
			Page<Sample> pageSample = samplePageService.getSamplesByText(text, filters, domains, pageable);

			
			PageMetadata pageMetadata = new PageMetadata(effectiveSize,
					pageSample.getNumber(), pageSample.getTotalElements(), pageSample.getTotalPages());
			
			Resources<Resource<Sample>> resources = new PagedResources<>(pageSample.getContent().stream()
					.map(s -> sampleResourceAssembler.toResource(s))
					.collect(Collectors.toList()), pageMetadata);			 


			//if theres more than one page, link to first and last
			if (pageSample.getTotalPages() > 1) {
				resources.add(getPageLink(decodedText, decodedFilter, 0, effectiveSize, sort, Link.REL_FIRST));				
			}
			//if there was a previous page, link to it
			if (effectivePage > 0) {
				resources.add(getPageLink(decodedText, decodedFilter, effectivePage-1, effectiveSize, sort, Link.REL_PREVIOUS));
			}
			resources.add(getPageLink(decodedText, decodedFilter, effectivePage, effectiveSize, sort, Link.REL_SELF));
			
			//if there is a next page, link to it 
			if (effectivePage < pageSample.getTotalPages()-1) {
				resources.add(getPageLink(decodedText, decodedFilter, effectivePage+1, effectiveSize, sort, Link.REL_NEXT));
			}
			//if theres more than one page, link to first and last
			if (pageSample.getTotalPages() > 1) {
				resources.add(getPageLink(decodedText, decodedFilter, pageSample.getTotalPages(), effectiveSize, sort, Link.REL_LAST));				
			}

			//if we are on the first page and not sorting
			if (effectivePage==0 && (sort==null || sort.length==0)) {
				resources.add(getCursorLink(decodedText, decodedFilter, "*", effectiveSize, "cursor"));
			}
			
			//if there is no search term, and on first page, add a link to use search
			//TODO
//			if (text.trim().length() == 0 && page == 0) {
//				resources.add(LinkUtils.cleanLink(ControllerLinkBuilder
//					.linkTo(ControllerLinkBuilder.methodOn(SamplesRestController.class)
//						.searchHal(null, filter, null, page, effectiveSize, sort, null))
//					.withRel("search")));
//			}
			
			resources.add(SampleAutocompleteRestController.getLink(decodedText, decodedFilter, null, "autocomplete"));
					
			resources.add(ControllerLinkBuilder
				.linkTo(ControllerLinkBuilder.methodOn(SampleRestController.class)
					.getSampleHal(null, false))
				.withRel("sample"));
			
			/*
			if (filters.stream().allMatch(f -> !f.getType().equals(FilterType.DATE_FILTER))) {
	
				String[] templatedFilters = new String[1];
				templatedFilters[0] = FilterType.DATE_FILTER.getSerialization()+":update:from{ISO-8601from}until{ISO-8601until}";
				pagedResources.add(ControllerLinkBuilder
						.linkTo(ControllerLinkBuilder.methodOn(SamplesRestController.class)
								.searchHal(text, templatedFilters, null, null))
						.withRel("samplesbyUpdateDate"));
			}
			*/
			
			return resources;
		}
		
		//TODO add search link
	}
	
	private Order parseSort(String sort) {
		if(sort.endsWith(",desc")) {
			return new Order(Sort.Direction.DESC, sort.substring(0, sort.length()-5));
		} else if(sort.endsWith(",asc")) {
			return new Order(Sort.Direction.ASC, sort.substring(0, sort.length()-4));
		} else {
			return new Order(null, sort);
		}
	}
	
	/**
	 * ControllerLinkBuilder seems to have problems linking to the same controller?
	 * Split out into manual manipulation for greater control
	 * 
	 * @param text
	 * @param filter
	 * @param cursor
	 * @param size
	 * @param rel
	 * @return
	 */
	public static Link getCursorLink(String text, String[] filter, String cursor, int size, String rel) {
		UriComponentsBuilder builder = ControllerLinkBuilder.linkTo(SamplesRestController.class)
				.toUriComponentsBuilder();
		if (text != null && text.trim().length() > 0) {
			builder.queryParam("text", text);
		}
		if (filter != null) {
			for (String filterString : filter) {
				builder.queryParam("filter",filterString);				
			}
		}
		builder.queryParam("cursor", cursor);
		builder.queryParam("size", size);
		return new Link(builder.toUriString(), rel);
	}
	
	public static Link getPageLink(String text, String[] filter, int page, int size, String[] sort, String rel) {
		UriComponentsBuilder builder = ControllerLinkBuilder.linkTo(SamplesRestController.class)
				.toUriComponentsBuilder();
		if (text != null && text.trim().length() > 0) {
			builder.queryParam("text", text);
		}
		if (filter != null) {
			for (String filterString : filter) {
				builder.queryParam("filter",filterString);				
			}
		}
		builder.queryParam("page", page);
		builder.queryParam("size", size);
		if (sort != null) {
			for (String sortString : sort) {
				builder.queryParam("sort",sortString);				
			}
		}
		return new Link(builder.toUriString(), rel);
	}
	


	@PreAuthorize("isAuthenticated()")
	@PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<Resource<Sample>> post(@RequestBody Sample sample,
			@RequestParam(name = "setupdatedate", required = false, defaultValue="true") boolean setUpdateDate,
            @RequestParam(name = "setfulldetails", required = false, defaultValue = "false") boolean setFullDetails) {
		
		log.debug("Recieved POST for "+sample);
		sample = bioSamplesAapService.handleSampleDomain(sample);

		//limit use of this method to write super-users only
		if (bioSamplesAapService.isWriteSuperUser() && setUpdateDate) {
			sample = Sample.build(sample.getName(), sample.getAccession(), sample.getDomain(), 
					sample.getRelease(), Instant.now(),
					sample.getCharacteristics(), sample.getRelationships(), sample.getExternalReferences(), 
					sample.getOrganizations(), sample.getContacts(), sample.getPublications());
		}

		if (!setFullDetails) {
			sample = sampleManipulationService.removeLegacyFields(sample);
		}
		
		sample = sampleService.store(sample);
		
		// assemble a resource to return
		Resource<Sample> sampleResource = sampleResourceAssembler.toResource(sample);

		// create the response object with the appropriate status
		//TODO work out how to avoid using ResponseEntity but also set location header
		return ResponseEntity.created(URI.create(sampleResource.getLink("self").getHref())).body(sampleResource);
	}
	
}
