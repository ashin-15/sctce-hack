package org.sakshi.acquisition.notifications

/**
 * The positive list of source apps whose notifications Sakshi may read. The default is empty: nothing is observed
 * until the person picks apps. This is an application processing policy, not a per-chat permission granted by Android.
 * The check uses only the package name, so it can run before any notification extras are touched.
 */
public class PackageAllowlist private constructor(private val packages: Set<String>) {
    /** The package names in the list. */
    public val names: Set<String> get() = packages

    public val isEmpty: Boolean get() = packages.isEmpty()

    public operator fun contains(packageName: String): Boolean = packageName in packages

    /** A copy with [packageName] added. @throws IllegalArgumentException for a name that is not a package name. */
    public fun with(packageName: String): PackageAllowlist = of(packages + packageName)

    public fun without(packageName: String): PackageAllowlist = PackageAllowlist(packages - packageName)

    override fun equals(other: Any?): Boolean = other is PackageAllowlist && other.packages == packages

    override fun hashCode(): Int = packages.hashCode()

    override fun toString(): String = "PackageAllowlist(${packages.size} packages)"

    public companion object {
        /** Nothing is observed. */
        public val EMPTY: PackageAllowlist = PackageAllowlist(emptySet())

        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        /** True for a syntactically valid Android package name of at most 255 characters. */
        public fun isValidName(name: String): Boolean = name.length <= 255 && PACKAGE_NAME.matches(name)

        /** @throws IllegalArgumentException when any name is not a valid package name. */
        public fun of(names: Collection<String>): PackageAllowlist {
            names.forEach { require(isValidName(it)) { "Not a package name: $it" } }
            return PackageAllowlist(names.toSet())
        }
    }
}
