package com.echogallery.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.asm.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GoalControllerBindingTests {
    @Test void routesBindWithoutCompilerParameterNames() throws Exception {
        byte[] bytes;
        try(var input=GoalController.class.getResourceAsStream("/com/echogallery/goal/GoalController.class")) {
            var writer=new ClassWriter(0);new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9,writer){},ClassReader.SKIP_DEBUG);bytes=writer.toByteArray();
        }
        class Loader extends ClassLoader { Loader(){super(GoalController.class.getClassLoader());}Class<?> load(){return defineClass(GoalController.class.getName(),bytes,0,bytes.length);} }
        var type=new Loader().load();for(var method:type.getDeclaredMethods())for(var p:method.getParameters())assertThat(p.isNamePresent()).isFalse();
        var service=mock(GoalService.class);var mvc=MockMvcBuilders.standaloneSetup(type.getConstructor(GoalService.class).newInstance(service)).build();
        mvc.perform(get("/api/goals").param("keyword","寫作").param("status","PAUSED").param("page","2").param("size","10")).andExpect(status().isOk());
        verify(service).list("寫作","PAUSED",false,2,10);
        mvc.perform(get("/api/goals").param("statuses","ACTIVE","PAUSED").param("archiveScope","ALL").param("pinned","true").param("sort","pinOrder")).andExpect(status().isOk());
        verify(service).list(null,null,false,0,20,java.util.List.of(GoalData.Status.ACTIVE,GoalData.Status.PAUSED),GoalData.ArchiveScope.ALL,true,"pinOrder");
        mvc.perform(get("/api/goals/42")).andExpect(status().isOk());verify(service).detail(42L);
        mvc.perform(get("/api/goals/options").param("kind","THREAD").param("page","1")).andExpect(status().isOk());verify(service).options(GoalData.Kind.THREAD,null,1);
        mvc.perform(get("/api/goals/options").param("kind","THREAD").param("experimentId","7")).andExpect(status().isOk());verify(service).options(GoalData.Kind.THREAD,null,0,7L);
        mvc.perform(get("/api/goal-associations").param("kind","CARD").param("id","9")).andExpect(status().isOk());verify(service).associations(new GoalData.Source(GoalData.Kind.CARD,9L,false));
        mvc.perform(delete("/api/goals/42").param("version","3")).andExpect(status().isOk());verify(service).delete(42L,3L);
    }
}
