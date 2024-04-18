package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.passes.UndeclaredVariable;
import pt.up.fe.comp2024.analysis.passes.TypeError;
import pt.up.fe.comp2024.analysis.passes.InvalidArrayAccess;
import pt.up.fe.comp2024.analysis.passes.UndefinedMethod;
import pt.up.fe.comp2024.analysis.passes.ThisInStaticMethod;
import pt.up.fe.comp2024.analysis.passes.DuplicatedElement;
import pt.up.fe.comp2024.analysis.passes.InvalidMethodDeclaration;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The implementation of the JmmAnalysis interface.
 */
public class JmmAnalysisImpl implements JmmAnalysis {

    private final List<AnalysisPass> analysisPasses;

    /**
     * Create a new instance of the JmmAnalysisImpl class.
     */
    public JmmAnalysisImpl() {

        this.analysisPasses = new ArrayList<>();

        // Add all analysis passes
        analysisPasses.add(new DuplicatedElement());
        analysisPasses.add(new ThisInStaticMethod());
        analysisPasses.add(new InvalidMethodDeclaration());
        analysisPasses.add(new UndeclaredVariable());
        analysisPasses.add(new UndefinedMethod());
        analysisPasses.add(new TypeError());
        analysisPasses.add(new InvalidArrayAccess());
    }

    /**
     * Perform the semantic analysis of the program.
     *
     * @param parserResult The result of the parsing phase.
     * @return The result of the semantic analysis.
     */
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        JmmNode rootNode = parserResult.getRootNode();

        SymbolTable table = JmmSymbolTableBuilder.build(rootNode);

        List<Report> reports = new ArrayList<>();

        // Visit all nodes in the AST
        for (var analysisPass : analysisPasses) {
            try {
                var passReports = analysisPass.analyze(rootNode, table);

                reports.addAll(passReports);

                // Halt if there are any reports
                if (!reports.isEmpty()) break;

            } catch (Exception e) {
                reports.add(Report.newError(Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" + analysisPass.getClass() + "'",
                        e)
                );
            }

        }

        return new JmmSemanticsResult(parserResult, table, reports);
    }
}
