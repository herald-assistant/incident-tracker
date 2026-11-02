package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;

public record GitLabRepositoryComparison(String baselineCommit, String targetCommit,
        List<Change> changes, boolean fileListComplete, List<String> limitations) {
    public GitLabRepositoryComparison {
        changes = List.copyOf(changes);
        limitations = List.copyOf(limitations);
    }

    public record Change(String oldPath, String newPath, String oldBlobId, String newBlobId,
            String oldMode, String newMode, String patch, boolean patchComplete) {
        public Change {
            if(oldPath==null&&newPath==null||!validSide(oldPath,oldBlobId,oldMode)||!validSide(newPath,newBlobId,newMode)
                ||patchComplete&&(patch==null||patch.isBlank()))throw new IllegalArgumentException("Invalid Git change.");
        }
        private static boolean validSide(String path,String id,String mode) {
            return path==null?id==null&&mode==null:GitLabRepositoryPath.isSafePath(path,false)&&id!=null
                &&id.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")&&java.util.Set.of("100644","100755","120000","160000").contains(mode);
        }
        public String changeType() {
            if (oldPath == null) return "ADDED";
            if (newPath == null) return "REMOVED";
            return oldPath.equals(newPath) ? "MODIFIED" : "RENAMED";
        }
    }
}