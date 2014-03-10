package edu.cmu.scs.azurite.ui.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import edu.cmu.scs.azurite.commands.runtime.RuntimeDC;
import edu.cmu.scs.azurite.model.undo.SelectiveUndoEngine;
import edu.cmu.scs.fluorite.util.Utilities;

public class StepwiseUndoInRegionHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<RuntimeDC> dcs = HandlerUtilities.getOperationsInSelectedRegion();
		if (dcs == null) {
			return null;
		}
		
		if (dcs.isEmpty()) {
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			MessageDialog.openInformation(shell,
					"Azurite - Stepwise Undo in Region",
					"No more operations to be undone in this region.");
			
			return null;
		}

		// get active editor
		IEditorPart editorPart = Utilities.getActiveEditor();
		
		if (!(editorPart instanceof AbstractTextEditor)) {
			// Do nothing.
			return null;
		}
		
		ITextEditor editor = (ITextEditor) editorPart;
		IDocumentProvider dp = editor.getDocumentProvider();
		IDocument doc = dp.getDocument(editor.getEditorInput());
		
		SelectiveUndoEngine.getInstance().doSelectiveUndo(dcs, doc);
		
		return null;
	}

}
