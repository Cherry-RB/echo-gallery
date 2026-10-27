package com.echogallery.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Opcodes;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExplorationControllerBindingTests {
    // 移除編譯時參數名稱，重現 IDE 未使用 -parameters 的啟動情境。
    private Object withoutParameterNames(Class<?> controller, Object service) throws Exception {
        String resource = "/" + controller.getName().replace('.', '/') + ".class";
        byte[] bytecode;
        try (var input = controller.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("找不到控制器 bytecode");
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {}, ClassReader.SKIP_DEBUG);
            bytecode = writer.toByteArray();
        }
        class IsolatedLoader extends ClassLoader {
            IsolatedLoader() { super(controller.getClassLoader()); }
            Class<?> define() { return defineClass(controller.getName(), bytecode, 0, bytecode.length); }
        }
        Class<?> isolated = new IsolatedLoader().define();
        for (var method : isolated.getDeclaredMethods()) {
            for (var parameter : method.getParameters()) assertThat(parameter.isNamePresent()).isFalse();
        }
        Constructor<?> constructor = isolated.getConstructor(serviceType(controller));
        return constructor.newInstance(service);
    }

    private Class<?> serviceType(Class<?> controller) {
        return controller.getConstructors()[0].getParameterTypes()[0];
    }

    private MockMvc mvc(Class<?> controller, Object service) throws Exception {
        return MockMvcBuilders.standaloneSetup(withoutParameterNames(controller, service)).build();
    }

    @Test
    void activityStateBindsExperimentIdWithoutCompilerParameterNames() throws Exception {
        var service = mock(ExplorationActivityService.class);
        mvc(ExplorationActivityController.class, service).perform(get("/api/experiments/41/activity"))
            .andExpect(status().isOk());
        verify(service).state(41L);
    }

    @Test
    void methodCandidatesBindFiltersAndPaginationWithoutCompilerParameterNames() throws Exception {
        var service = mock(ExplorationActivityService.class);
        var mvc = mvc(ExplorationActivityController.class, service);
        mvc.perform(get("/api/experiments/41/activity/methods")
                .param("threadId", "3").param("keyword", "閱讀").param("page", "1").param("size", "12"))
            .andExpect(status().isOk());
        verify(service).methods(41L, 3L, "閱讀", 1, 12);
        mvc.perform(get("/api/experiments/41/activity/methods")).andExpect(status().isOk());
        verify(service).methods(41L, null, "", 0, 20);
    }

    @Test
    void findingFiltersAndHistoryBindNullableMethodAndRequiredThread() throws Exception {
        var service = mock(ExplorationActivityService.class);
        var mvc = mvc(ExplorationActivityController.class, service);
        mvc.perform(get("/api/experiments/41/activity/records")
                .param("threadId", "3").param("unassigned", "true").param("page", "2").param("size", "10"))
            .andExpect(status().isOk());
        verify(service).records(41L, 3L, null, true, 2, 10);
        mvc.perform(get("/api/experiments/41/activity/history").param("threadId", "3"))
            .andExpect(status().isOk());
        verify(service).history(41L, 3L, null);
        mvc.perform(get("/api/experiments/41/activity/history").param("threadId", "3").param("methodId", "12"))
            .andExpect(status().isOk());
        verify(service).history(41L, 3L, 12L);
    }

    @Test
    void deletionBindsVersionsAndRejectsMissingVersionBeforeServiceCall() throws Exception {
        var service = mock(ExplorationActivityService.class);
        var mvc = mvc(ExplorationActivityController.class, service);
        mvc.perform(delete("/api/experiments/41/activity/methods/12")
                .param("expectedVersion", "4").param("deleteRecords", "true")).andExpect(status().isOk());
        verify(service).deleteMethod(41L, 12L, 4L, true);
        mvc.perform(delete("/api/experiments/41/activity/records/7").param("expectedVersion", "2"))
            .andExpect(status().isOk());
        verify(service).deleteRecord(41L, 7L, 2L);
        clearInvocations(service);
        mvc.perform(delete("/api/experiments/41/activity/records/7")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void organizationPreviewAndCommitBindExperimentIdWithoutCompilerParameterNames() throws Exception {
        var service = mock(ExplorationOrganizationService.class);
        var mvc = mvc(ExplorationOrganizationController.class, service);
        mvc.perform(post("/api/experiments/41/activity/organizations/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"HISTORY\",\"groups\":[{\"threadId\":3,\"methodId\":12,\"includeMethod\":true}]}"))
            .andExpect(status().isOk());
        verify(service).preview(eq(41L), any(ExplorationOrganizationRequests.Preview.class));
        mvc.perform(post("/api/experiments/41/activity/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"preview-token\",\"requestId\":\"00000000-0000-0000-0000-000000000001\",\"drafts\":[{\"key\":\"method:12\",\"text\":\"整理內容\"}]}"))
            .andExpect(status().isOk());
        verify(service).commit(eq(41L), any(ExplorationOrganizationRequests.Commit.class));
    }

    @Test
    void threadOptionsAndLegacyDeletionBindQueryNamesExplicitly() throws Exception {
        var service = mock(ExplorationThreadService.class);
        var mvc = mvc(ExplorationThreadController.class, service);
        mvc.perform(get("/api/experiments/41/threads/options")
                .param("keyword", "方向").param("cardId", "5").param("page", "2")).andExpect(status().isOk());
        verify(service).options(41L, "方向", 5L, 2);
        mvc.perform(delete("/api/experiments/41/threads/3/methods/12").param("expectedVersion", "4"))
            .andExpect(status().isOk());
        verify(service).deleteMethod(41L, 3L, 12L, false, 4L);
        mvc.perform(delete("/api/experiments/41/threads/3/records/7").param("expectedVersion", "2"))
            .andExpect(status().isOk());
        verify(service).deleteRecord(41L, 3L, 7L, 2L);
    }
}
