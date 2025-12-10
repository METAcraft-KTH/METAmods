#include <filesystem>

void removeFrom(const std::filesystem::path& p) {
	auto src = p / "remappedSrc";
	if (std::filesystem::exists(src) && std::filesystem::is_directory(src)) {
		std::filesystem::remove_all(src);
	}
	for (auto dir : std::filesystem::directory_iterator(p)) {
		if (std::filesystem::is_directory(dir)) {
			removeFrom(dir);
		}
	}
}

void removeMigratedMappings() {
	std::filesystem::path p = ".";
	removeFrom(p);
}


int main() {
	removeMigratedMappings();
}
