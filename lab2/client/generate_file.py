import os

def generate_file(filename, size_kb):
    """
    Генерирует файл заданного размера в КБ.

    :param filename: Имя создаваемого файла.
    :param size_kb: Размер файла в килобайтах.
    """
    try:
        size_bytes = size_kb * 1024  # Переводим КБ в байты
        with open(filename, 'wb') as f:
            # Метод 1: Заполнение нулями
            # f.write(b'\0' * size_bytes)

            # Метод 2: Использование seek для установки размера файла
            f.seek(size_bytes - 1)
            f.write(b'\0')

        print(f"Файл '{filename}' успешно создан размером {size_kb} КБ.")
    except Exception as e:
        print(f"Произошла ошибка: {e}")

def main():
    print("Генератор файла заданного размера")
    filename = input("Введите имя файла (например, output.bin): ").strip()

    while True:
        try:
            size_kb = int(input("Введите размер файла в КБ: ").strip())
            if size_kb <= 0:
                print("Размер должен быть положительным числом. Попробуйте снова.")
                continue
            break
        except ValueError:
            print("Некорректный ввод. Пожалуйста, введите целое число.")

    generate_file(filename, size_kb)

if __name__ == "__main__":
    main()
