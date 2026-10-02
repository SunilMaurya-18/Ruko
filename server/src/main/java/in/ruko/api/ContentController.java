package in.ruko.api;

import in.ruko.content.HowRukoDecides;
import in.ruko.content.OfficialLinks;
import in.ruko.content.RecoveryContent;
import in.ruko.pipeline.Lang;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    private final HowRukoDecides howRukoDecides;
    private final RecoveryContent recovery;
    private final OfficialLinks links;

    public ContentController(HowRukoDecides howRukoDecides, RecoveryContent recovery, OfficialLinks links) {
        this.howRukoDecides = howRukoDecides;
        this.recovery = recovery;
        this.links = links;
    }

    @GetMapping(path = "/how-ruko-decides", produces = MediaType.APPLICATION_JSON_VALUE)
    public HowRukoDecides.Page howRukoDecides(@RequestParam(defaultValue = "hi") Lang lang) {
        return howRukoDecides.page(lang);
    }

    @GetMapping(path = "/recovery", produces = MediaType.APPLICATION_JSON_VALUE)
    public RecoveryContent.Page recovery(@RequestParam(defaultValue = "hi") Lang lang) {
        return recovery.page(lang);
    }

    @GetMapping(path = "/links", produces = MediaType.APPLICATION_JSON_VALUE)
    public OfficialLinks.Page links(@RequestParam(defaultValue = "hi") Lang lang) {
        return links.page(lang);
    }
}
