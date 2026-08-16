package org.huxerui.clion.resources;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.Nullable;

final class HuxerUIResourceNavigationElement extends FakePsiElement {
    private final PsiFile file_;
    private final String identifier_;
    private final int offset_;

    HuxerUIResourceNavigationElement(PsiFile file, String identifier, int offset) {
        file_ = file;
        identifier_ = identifier;
        offset_ = offset;
    }

    @Override
    public PsiElement getParent() {
        return file_;
    }

    @Override
    public PsiFile getContainingFile() {
        return file_;
    }

    @Override
    public String getName() {
        return identifier_;
    }

    @Override
    public String getPresentableText() {
        return identifier_;
    }

    @Override
    public @Nullable String getLocationString() {
        return file_.getVirtualFile().getName();
    }

    @Override
    public int getTextOffset() {
        return offset_;
    }

    @Override
    public void navigate(boolean request_focus) {
        new OpenFileDescriptor(getProject(), file_.getVirtualFile(), offset_).navigate(request_focus);
    }

    @Override
    public boolean canNavigate() {
        return file_.isValid() && file_.getVirtualFile().isValid();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}
