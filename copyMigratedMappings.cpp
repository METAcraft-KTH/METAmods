#include <filesystem>

void migrate(const std::filesystem::path& p) {
	auto src = p / "remappedSrc";
	if (std::filesystem::exists(src) && std::filesystem::is_directory(src)) {
		auto target = p / "src/main/java";
		for (auto dir : std::filesystem::directory_iterator(target)) {
			std::filesystem::remove_all(dir);
		}
		std::filesystem::copy(src, target, std::filesystem::copy_options::recursive);
		std::filesystem::remove_all(src);
	}
	for (auto dir : std::filesystem::directory_iterator(p)) {
		if (std::filesystem::is_directory(dir)) {
			migrate(dir);
		}
	}
}

void copyMigratedMappings() {
	std::filesystem::path p = ".";
	migrate(p);
}


int main() {
	copyMigratedMappings();
}
