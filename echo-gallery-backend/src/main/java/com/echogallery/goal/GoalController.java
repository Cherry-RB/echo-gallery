package com.echogallery.goal;

import static com.echogallery.goal.GoalData.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class GoalController {
    private final GoalService goals;
    @GetMapping("/api/goals") public Object list(@RequestParam(name="keyword",required=false) String keyword,
            @RequestParam(name="status",required=false) String status,@RequestParam(name="archived",defaultValue="false") boolean archived,
            @RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="size",defaultValue="20") int size,
            @RequestParam(name="statuses",required=false) java.util.List<Status> statuses,
            @RequestParam(name="archiveScope",required=false) ArchiveScope archiveScope,
            @RequestParam(name="pinned",required=false) Boolean pinned,
            @RequestParam(name="sort",defaultValue="created") String sort){
        if(statuses==null&&archiveScope==null&&pinned==null&&"created".equals(sort))return goals.list(keyword,status,archived,page,size);
        return goals.list(keyword,status,archived,page,size,statuses,archiveScope,pinned,sort);
    }
    @GetMapping("/api/goals/overview") public Object overview(){return goals.overview();}
    @GetMapping("/api/goals/options") public Object options(@RequestParam("kind") Kind kind,@RequestParam(name="keyword",required=false) String keyword,@RequestParam(name="page",defaultValue="0") int page,
            @RequestParam(name="experimentId",required=false) Long experimentId){return experimentId==null?goals.options(kind,keyword,page):goals.options(kind,keyword,page,experimentId);}
    @PostMapping("/api/goals/tracking-preview") public Object preview(@Valid @RequestBody TrackingPreview request){return goals.preview(request);}
    @PostMapping("/api/goals/{id}/pin-order") public Object pinOrder(@PathVariable("id") Long id,@Valid @RequestBody PinOrder request){return goals.pinOrder(id,request);}
    @GetMapping("/api/goals/{id}") public Object detail(@PathVariable("id") Long id){return goals.detail(id);}
    @PostMapping("/api/goals") public Object create(@Valid @RequestBody Save request){return goals.save(null,request);}
    @PutMapping("/api/goals/{id}") public Object save(@PathVariable("id") Long id,@Valid @RequestBody Save request){return goals.save(id,request);}
    @DeleteMapping("/api/goals/{id}") public void delete(@PathVariable("id") Long id,@RequestParam("version") long version){goals.delete(id,version);}
    @GetMapping("/api/goals/{id}/revisions") public Object revisions(@PathVariable("id") Long id){return goals.revisions(id);}
    @GetMapping("/api/goals/{id}/sources") public Object sources(@PathVariable("id") Long id){return goals.sources(id);}
    @GetMapping("/api/goals/{id}/metrics") public Object metrics(@PathVariable("id") Long id){return goals.metrics(id);}
    @GetMapping("/api/goals/{id}/evidence") public Object evidence(@PathVariable("id") Long id,@RequestParam(name="metric",required=false) String metric,
            @RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="size",defaultValue="20") int size){return goals.evidence(id,metric,page,size);}
    @PutMapping("/api/goals/{id}/evidence/{linkId}") public Object evidence(@PathVariable("id") Long id,@PathVariable("linkId") String linkId,@Valid @RequestBody EvidenceChange request){return goals.evidence(id,linkId,request);}
    @GetMapping("/api/goal-board/preferences") public Object preference(){return goals.preference();}
    @PutMapping("/api/goal-board/preferences") public Object preference(@Valid @RequestBody Preference request){return goals.preference(request);}
    @PostMapping("/api/goal-board/cards") public Object board(@Valid @RequestBody Board request){return goals.board(request);}
    @PostMapping("/api/goal-associations/summary") public Object associations(@Valid @RequestBody Associations request){return goals.associations(request);}
    @GetMapping("/api/goal-associations") public Object associations(@RequestParam("kind") Kind kind,@RequestParam("id") Long id){return goals.associations(new Source(kind,id,false));}
}
