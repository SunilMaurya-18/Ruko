package in.ruko;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import in.ruko.infra.SafeLog;

@AnalyzeClasses(packages = "in.ruko", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule onlySafeLogTouchesLoggingApis = noClasses()
            .that().doNotBelongToAnyOf(SafeLog.class)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.slf4j..",
                    "java.util.logging..",
                    "org.apache.commons.logging..",
                    "org.apache.logging.log4j..",
                    "ch.qos.logback..")
            .orShould().dependOnClassesThat().belongToAnyOf(System.Logger.class)
            .because("all logging goes through SafeLog, which cannot accept message content");

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
