package com.unhuman.dependencyresolver.tgf;

public class Version implements Comparable {
    private static final int LEFT_GREATER = 1;
    private static final int RIGHT_GREATER = -1;
    private static final int EQUALS = 0;

    private String version;
    private String[] versionData;
    private String suffix;
    private boolean isSemVer;

    public Version(String versionInfo) {
        version = versionInfo;
        isSemVer = false;

        // find a suffix
        String[] versionParts = versionInfo.split("-", 2);

        // update the versionInfo
        versionInfo = versionParts[0];
        if (versionParts.length > 1) {
            suffix = versionParts[1];
        }

        versionData = versionInfo.split("\\.");
        if (versionData.length >= 3) {
            isSemVer = true;
            for (int i = 0; i < 3; i++) {
                if (!checkNumber(versionData[i])) {
                    isSemVer = false;
                }
            }
        }
    }

    /**
     * Checks to see if a segment is a number
     * @param value
     * @return
     */
    private boolean checkNumber(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compares versions
     * @param o
     * @return
     */
    @Override
    public int compareTo(Object o) {
        if (!(o instanceof Version)) {
            throw new RuntimeException("Cannot compare Version with " + o.getClass().getSimpleName());
        }

        Version other = (Version) o;

        // Semantic versioning is better than all other versions
        if (isSemVer && !other.isSemVer) {
            return LEFT_GREATER;
        }
        if (other.isSemVer && !isSemVer) {
            return RIGHT_GREATER;
        }

        // compare all the segments that we can
        for (int i = 0; i < versionData.length; i++) {
            if (other.versionData.length - 1 < i) {
                return LEFT_GREATER;
            }

            int segmentCompare = compareSegment(versionData[i], other.versionData[i]);
            if (segmentCompare != EQUALS) {
                return segmentCompare;
            }
        }

        if (versionData.length == other.versionData.length) {
            return compareSuffix(suffix, other.suffix);
        }

        // the right side had more info - so it's more recent
        return RIGHT_GREATER;
    }

    /**
     * missing suffix is a better value than one that exists (think pre-release versions)
     *
     * @param left
     * @param right
     * @return
     */
    protected int compareSuffix(String left, String right) {
        if (left == null) {
            if (right == null) {
                return EQUALS;
            } else {
                return LEFT_GREATER;
            }
        }

        if (right == null)
        {
            return RIGHT_GREATER;
        }

        int compareResult = left.compareTo(right);
        return (compareResult == 0) ? 0 : compareResult / Math.abs(compareResult);
    }

    /**
     * Compares version information per segment
     * @param left
     * @param right
     * @return
     */
    protected int compareSegment(String left, String right) {
        boolean leftNumber = checkNumber(left);
        boolean rightNumber = checkNumber(right);

        if (leftNumber) {
            if (!rightNumber) {
                return LEFT_GREATER;
            }

            // both versions are numbers - compare this
            Long leftLong = Long.parseLong(left);

            int compareResult = leftLong.compareTo(Long.parseLong(right));
            return (compareResult == 0) ? 0 : compareResult / Math.abs(compareResult);
        }

        if (!leftNumber && rightNumber) {
            return RIGHT_GREATER;
        }

        // We have to use something for this element, so...  Just string compare
        int compareResult = left.compareTo(right);
        return (compareResult == 0) ? 0 : compareResult / Math.abs(compareResult);
    }

    public String getVersion() {
        return version;
    }

    protected boolean isSemVer() {
        return isSemVer;
    }
}
