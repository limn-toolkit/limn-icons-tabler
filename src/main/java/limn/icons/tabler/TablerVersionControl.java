package limn.icons.tabler;

/**
 * Tabler's <b>Version control</b> icons, one constant per name.
 *
 * <p>Generated. The set is split across one enum per upstream category because a single
 * enum cannot hold it: a class initialiser is capped at 64KB of bytecode and every constant
 * costs roughly twenty of them, so one enum holding every Tabler name does not compile at
 * all. The categories are upstream's own, and the largest of them is comfortably inside the
 * ceiling.
 */
public enum TablerVersionControl implements TablerIcon {

    GIT_BRANCH("git-branch"),
    GIT_BRANCH_CHECK("git-branch-check"),
    GIT_BRANCH_DELETED("git-branch-deleted"),
    GIT_BRANCH_X("git-branch-x"),
    GIT_CHERRY_PICK("git-cherry-pick"),
    GIT_COMMIT("git-commit"),
    GIT_COMPARE("git-compare"),
    GIT_FORK("git-fork"),
    GIT_MERGE("git-merge"),
    GIT_MERGE_QUEUE("git-merge-queue"),
    GIT_PULL_REQUEST("git-pull-request"),
    GIT_PULL_REQUEST_CLOSED("git-pull-request-closed"),
    GIT_PULL_REQUEST_CONFLICT("git-pull-request-conflict"),
    GIT_PULL_REQUEST_DRAFT("git-pull-request-draft"),
    GIT_PULL_REQUEST_LOCKED("git-pull-request-locked"),
    GIT_PULL_REQUEST_UNLISTED("git-pull-request-unlisted");

    private final String iconName;

    TablerVersionControl(String iconName) {
        this.iconName = iconName;
    }

    @Override
    public String iconName() {
        return iconName;
    }
}
