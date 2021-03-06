package uk.ac.ebi.biosamples.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mged.magetab.error.ErrorItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.arrayexpress2.magetab.listener.ErrorItemListener;
import uk.ac.ebi.arrayexpress2.sampletab.datamodel.SampleData;
import uk.ac.ebi.arrayexpress2.sampletab.parser.SampleTabParser;
import uk.ac.ebi.biosamples.service.SampleTabService;
import uk.ac.ebi.biosamples.service.SampleTabService.AssertingSampleTabOwnershipException;
import uk.ac.ebi.biosamples.service.SampleTabService.ConflictingSampleTabOwnershipException;
import uk.ac.ebi.biosamples.service.SampleTabService.DuplicateDomainSampleException;

//TODO this need to be updated to use jwt and aap
//@RestController
public class SampleTabV4Controller {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private SampleTabService sampleTabService;

	@RequestMapping(method = RequestMethod.POST, value = "v4", consumes = {MediaType.TEXT_PLAIN_VALUE, "application/text"})
	public ResponseEntity<String> acceptSampleTab(@RequestBody String sampleTab,
			@RequestParam(name = "setupdatedate", required = false, defaultValue="true") boolean setUpdateDate,
			@RequestParam(name = "domain", required = false) String domain,
			HttpServletRequest request, HttpServletResponse response) {

		log.trace("recieved SampleTab submission \n"+sampleTab);

        //setup parser to listen for errors
        SampleTabParser<SampleData> parser = new SampleTabParser<SampleData>();
        
        List<ErrorItem> errorItems;
        errorItems = new ArrayList<ErrorItem>();
        parser.addErrorItemListener(new ErrorItemListener() {
            public void errorOccurred(ErrorItem item) {
                errorItems.add(item);
            }
        });
        SampleData sampledata = null;
        
        InputStream stream = null;
        try {
        	stream = new ByteArrayInputStream(sampleTab.getBytes(StandardCharsets.UTF_8));
            //parse the input into sampletab
            //will also validate
            sampledata = parser.parse(stream);
        } catch (ParseException e) {
            //catch parsing errors for malformed submissions
            log.error("parsing error", e);
            return ResponseEntity.badRequest().body("Unable to read SampleTab. Error is "+e.getLocalizedMessage());
        } finally {
        	if (stream != null) {
        		try {
					stream.close();
				} catch (IOException e) {
					// do nothing
				}
        	}
        }

        //no errors
        if (errorItems.size() > 0) {
        	//at least some errors were discovered during parsing
        	StringBuilder sb = new StringBuilder();
        	sb.append("Unable to read SampleTab. Errors are:\n");
        	for (ErrorItem ei : errorItems) {
        		sb.append("  "+ei.getMesg()+"\n");
        	}
            return ResponseEntity.badRequest().body(sb.toString());
        }
        
        String jwt = null; //TODO get from request
                

        if (domain == null) {
        	//TODO no explicit domain, read from JWT if only one
        }
        
        
        //TODO do AAP domain property
        //TODO do JWT properly
        try {
			sampleTabService.saveSampleTab(sampledata,
					"self.BiosampleIntegrationTest",
					false,
					setUpdateDate,
					true);
		} catch (DuplicateDomainSampleException | ConflictingSampleTabOwnershipException | AssertingSampleTabOwnershipException e) {
			log.error("Problem saving sampletab ", e);
			return ResponseEntity.badRequest().body(e.getMessage());
		}
        
        return ResponseEntity.ok("");
	}
}
